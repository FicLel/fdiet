"""Runs one food sync at a time in the background: preflight -> scrape -> ingest -> match.

Fire and forget: `start` answers at once with the run, and the run's status is read back with
`get`. The status is written to `data/food-syncs/<id>.json` at every step, so it survives a
restart; a run found `running` on startup is marked `interrupted`, because nothing resumes it.

- **preflight** - OpenSearch and the fdiet backend must answer before hours of scraping start.
- **scrape** - one scrape job per supermarket asked for, through the `JobManager`, so its
  cooldown, politeness and refusal handling all apply. A supermarket inside its cooldown, or
  refused, is not scraped again: its last complete snapshot is used, and the run says so.
- **ingest** - every supermarket's latest complete snapshot into a new products index.
- **match** - every food of the fdiet backend searched in that index.
"""

import asyncio
import logging
import uuid
from datetime import datetime, timezone
from pathlib import Path

from app import logs
from app.config import Settings
from app.foodsync import phases
from app.foodsync.fdiet_api import FdietApi
from app.foodsync.opensearch import SearchClient
from app.jobs import AlreadyRunningError, CooldownError, JobManager
from app.models import FoodSync, FoodSyncOptions, FoodSyncPhase, FoodSyncStatus, JobStatus, ScrapeEntry
from app.scrapers import SCRAPERS
from app.store import SnapshotReader

log = logging.getLogger(__name__)
MAX_RUNS_KEPT = 100
FOLDER = "food-syncs"
_UNFINISHED = (FoodSyncStatus.QUEUED, FoodSyncStatus.RUNNING)


class FoodSyncRunningError(Exception):
    def __init__(self, run: FoodSync) -> None:
        super().__init__(f"food sync {run.id} is {run.status} (phase {run.phase}); cancel it or wait")
        self.run = run


def _now() -> datetime:
    return datetime.now(timezone.utc)


class FoodSyncManager:
    def __init__(self, settings: Settings, jobs: JobManager, reader: SnapshotReader, search: SearchClient, api: FdietApi):
        self.settings = settings
        self.jobs = jobs
        self.reader = reader
        self.search = search
        self.api = api
        self._folder: Path = settings.data_dir / FOLDER
        self._folder.mkdir(parents=True, exist_ok=True)
        self._runs: dict[str, FoodSync] = self._load()
        self._task: asyncio.Task[None] | None = None
        self._active: str | None = None

    def list(self) -> list[FoodSync]:
        return sorted(self._runs.values(), key=lambda run: run.created_at, reverse=True)

    def get(self, sync_id: str) -> FoodSync | None:
        return self._runs.get(sync_id)

    def latest(self) -> FoodSync | None:
        runs = self.list()
        return runs[0] if runs else None

    def start(self, options: FoodSyncOptions) -> FoodSync:
        if self._active:
            raise FoodSyncRunningError(self._runs[self._active])
        run = FoodSync(id=uuid.uuid4().hex[:12], options=options, created_at=_now())
        self._runs[run.id] = run
        self._active = run.id
        self._save(run)
        self._task = asyncio.create_task(self._run(run), name=f"food-sync-{run.id}")
        self._forget_old_runs()
        return run

    async def cancel(self, sync_id: str) -> FoodSync | None:
        if sync_id == self._active and self._task and not self._task.done():
            self._task.cancel()
            await asyncio.gather(self._task, return_exceptions=True)
        return self._runs.get(sync_id)

    async def shutdown(self) -> None:
        if self._active:
            await self.cancel(self._active)

    async def _run(self, run: FoodSync) -> None:
        logs.bind(sync_id=run.id)
        run.status, run.started_at = FoodSyncStatus.RUNNING, _now()
        log.info("food sync %s started", run.id, extra={"options": run.options.model_dump()})
        try:
            self._enter(run, FoodSyncPhase.PREFLIGHT)
            version = await self.search.ping()
            foods = await self.api.ping()
            log.info("OpenSearch %s and the fdiet API (%d branded foods) answer", version, foods)
            if run.options.scrape:
                self._enter(run, FoodSyncPhase.SCRAPE)
                await self._scrape(run)
            self._enter(run, FoodSyncPhase.INGEST)
            await phases.ingest(self.settings, self.search, self.reader, list(SCRAPERS), run.ingest, lambda: self._save(run))
            self._enter(run, FoodSyncPhase.MATCH)
            await phases.match(self.settings, self.search, self.api, run.id, len(SCRAPERS), run.match, lambda: self._save(run))
            run.phase, run.status = FoodSyncPhase.DONE, FoodSyncStatus.SUCCEEDED
        except asyncio.CancelledError:
            run.status, run.error = FoodSyncStatus.CANCELLED, "cancelled"
            await self._cancel_own_jobs(run)
        except Exception as exc:
            log.exception("food sync %s failed in phase %s", run.id, run.phase)
            run.status, run.error = FoodSyncStatus.FAILED, f"{type(exc).__name__}: {exc}"
        finally:
            run.finished_at = _now()
            self._active = None
            self._save(run)
        level = logging.INFO if run.status is FoodSyncStatus.SUCCEEDED else logging.WARNING
        log.log(level, "food sync %s finished: %s", run.id, run.status, extra={
            "status": str(run.status), "error": run.error, "warnings": run.warnings,
            "products": run.ingest.products, "food_items": run.match.food_items.model_dump(),
            "composition": run.match.composition.model_dump(),
            "elapsed_seconds": round((run.finished_at - run.started_at).total_seconds(), 1)})

    def _enter(self, run: FoodSync, phase: FoodSyncPhase) -> None:
        run.phase = phase
        logs.bind(phase=str(phase))
        log.info("phase %s", phase)
        self._save(run)

    async def _scrape(self, run: FoodSync) -> None:
        for key in run.options.supermarkets:
            run.scrape[key] = self._start_job(key, run.options.force)
        self._save(run)
        while True:
            pending = False
            for entry in run.scrape.values():
                job = self.jobs.get(entry.job_id) if entry.job_id else None
                if job is None:
                    continue
                entry.status, entry.products = str(job.status), job.products
                if job.finished_at is None:
                    pending = True
                elif job.status is not JobStatus.SUCCEEDED and entry.note is None:
                    entry.note = f"{job.error or job.status}; its last complete snapshot, if any, is indexed"
                    run.warnings.append(f"{entry.supermarket}: {entry.note}")
            self._save(run)
            if not pending:
                return
            await asyncio.sleep(self.settings.food_sync_poll_seconds)

    def _start_job(self, key: str, force: bool) -> ScrapeEntry:
        try:
            job = self.jobs.start(key, force=force)
            return ScrapeEntry(supermarket=key, job_id=job.id, started_here=True, status=str(job.status))
        except AlreadyRunningError as exc:
            return ScrapeEntry(supermarket=key, job_id=exc.job.id, status=str(exc.job.status),
                               note="a sync of it was already running; waiting for that one")
        except CooldownError as exc:
            note = f"not scraped: cooling down until {exc.retry_at.isoformat()}; its last snapshot is indexed"
            log.info("%s %s", key, note, extra={"supermarket": key})
            return ScrapeEntry(supermarket=key, status="skipped", note=note)

    async def _cancel_own_jobs(self, run: FoodSync) -> None:
        for entry in run.scrape.values():
            if entry.started_here and entry.job_id:
                job = await self.jobs.cancel(entry.job_id)
                if job:
                    entry.status = str(job.status)

    def _save(self, run: FoodSync) -> None:
        path = self._folder / f"{run.id}.json"
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(run.model_dump_json(indent=2), encoding="utf-8")
        temporary.replace(path)

    def _load(self) -> dict[str, FoodSync]:
        runs: dict[str, FoodSync] = {}
        for path in self._folder.glob("*.json"):
            try:
                run = FoodSync.model_validate_json(path.read_text(encoding="utf-8"))
            except ValueError:
                log.warning("unreadable food sync status %s; ignored", path.name)
                continue
            if run.status in _UNFINISHED:
                run.status, run.error = FoodSyncStatus.INTERRUPTED, "the service stopped while this food sync ran"
                run.finished_at = run.finished_at or _now()
                self._save(run)
            runs[run.id] = run
        return runs

    def _forget_old_runs(self) -> None:
        for run in self.list()[MAX_RUNS_KEPT:]:
            self._runs.pop(run.id, None)
            (self._folder / f"{run.id}.json").unlink(missing_ok=True)
