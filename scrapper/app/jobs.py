"""Runs syncs in the background, one per supermarket at a time.

A sync is refused while one for the same supermarket runs (409), and within
`sync_cooldown_hours` of the last complete one - or of the last one the site refused -
unless forced (429). That cooldown is what keeps a caller on a timer, or an impatient
person, from turning a sync mechanism into a stream of full crawls.
"""

import asyncio
import logging
import uuid
from contextlib import aclosing
from datetime import datetime, timedelta, timezone

import httpx

from app import logs
from app.config import Settings
from app.models import ItemFailure, Job, JobStatus
from app.net.client import BlockedError, PoliteClient, RobotsDisallowedError, ScrapeError
from app.scrapers import SCRAPERS
from app.scrapers.base import MAX_RECENT_FAILURES, ScrapeContext
from app.store import SnapshotReader, SnapshotWriter

log = logging.getLogger(__name__)
MAX_JOBS_KEPT = 200


class AlreadyRunningError(Exception):
    def __init__(self, job: Job) -> None:
        super().__init__(f"a sync of {job.supermarket} is already {job.status}: job {job.id}")
        self.job = job


class CooldownError(Exception):
    def __init__(self, supermarket: str, retry_at: datetime) -> None:
        super().__init__(f"{supermarket} was synced recently; next sync allowed at {retry_at.isoformat()} (or force=true)")
        self.retry_at = retry_at


def _now() -> datetime:
    return datetime.now(timezone.utc)


class JobManager:
    def __init__(self, settings: Settings, reader: SnapshotReader, transport: httpx.AsyncBaseTransport | None = None):
        self.settings = settings
        self.reader = reader
        self._transport = transport
        self._jobs: dict[str, Job] = {}
        self._tasks: dict[str, asyncio.Task[None]] = {}
        self._active: dict[str, str] = {}  # supermarket -> job id
        self._refused_at: dict[str, datetime] = {}  # supermarket -> last blocked/disallowed run

    def list(self) -> list[Job]:
        return sorted(self._jobs.values(), key=lambda job: job.created_at, reverse=True)

    def get(self, job_id: str) -> Job | None:
        return self._jobs.get(job_id)

    def start(self, supermarket: str, limit: int | None = None, force: bool = False) -> Job:
        scraper = SCRAPERS[supermarket]
        active = self._active.get(supermarket)
        if active:
            raise AlreadyRunningError(self._jobs[active])
        if not force and limit is None:
            self._check_cooldown(supermarket)

        job = Job(id=uuid.uuid4().hex[:12], supermarket=supermarket, limit=limit, created_at=_now())
        self._jobs[job.id] = job
        self._active[supermarket] = job.id
        self._tasks[job.id] = asyncio.create_task(self._run(job, scraper), name=f"sync-{supermarket}")
        self._forget_old_jobs()
        return job

    async def cancel(self, job_id: str) -> Job | None:
        task = self._tasks.get(job_id)
        if task and not task.done():
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)
        return self._jobs.get(job_id)

    async def shutdown(self) -> None:
        for job_id in list(self._tasks):
            await self.cancel(job_id)

    def _check_cooldown(self, supermarket: str) -> None:
        cooldown = timedelta(hours=self.settings.sync_cooldown_hours)
        meta = self.reader.meta(supermarket)
        last = max((t for t in (meta.synced_at if meta else None, self._refused_at.get(supermarket)) if t), default=None)
        if last and _now() < last + cooldown:
            raise CooldownError(supermarket, last + cooldown)

    def _forget_old_jobs(self) -> None:
        finished = [job for job in self.list() if job.finished_at]
        for job in finished[MAX_JOBS_KEPT:]:
            self._jobs.pop(job.id, None)
            self._tasks.pop(job.id, None)

    def _record_failure(self, job: Job, failure: ItemFailure) -> None:
        log.warning("item failed: %s: %s", failure.url, failure.error, extra={"url": failure.url})
        job.failures += 1
        job.recent_failures.append(failure)
        del job.recent_failures[:-MAX_RECENT_FAILURES]

    async def _run(self, job: Job, scraper) -> None:
        logs.bind(job_id=job.id, supermarket=job.supermarket)
        job.status = JobStatus.RUNNING
        job.started_at = _now()
        log.info("sync %s of %s started", job.id, job.supermarket, extra={"limit": job.limit})
        progress_every = max(1, self.settings.log_progress_every)
        writer = SnapshotWriter(self.settings.data_dir, job.supermarket, job.started_at)
        job.output_file = str(writer.path)
        seen: set[str] = set()
        client = PoliteClient(self.settings, self._transport)
        try:
            ctx = ScrapeContext(client, self.settings, lambda failure: self._record_failure(job, failure))
            async with aclosing(scraper.products(ctx)) as products:
                async for product in products:
                    job.requests = client.requests
                    if product.source_id in seen:
                        continue
                    seen.add(product.source_id)
                    writer.write(product)
                    job.products = writer.count
                    if writer.count % progress_every == 0:
                        log.info("%d products so far", writer.count, extra=_progress(job))
                    if job.limit and writer.count >= job.limit:
                        break
            if writer.count == 0:
                job.status, job.error = JobStatus.FAILED, "the run finished without a single product"
            else:
                job.status = JobStatus.SUCCEEDED
        except BlockedError as exc:
            job.status, job.error = JobStatus.BLOCKED, str(exc)
        except RobotsDisallowedError as exc:
            job.status, job.error = JobStatus.DISALLOWED, str(exc)
        except asyncio.CancelledError:
            job.status, job.error = JobStatus.CANCELLED, "cancelled"
        except ScrapeError as exc:
            job.status, job.error = JobStatus.FAILED, str(exc)
        except Exception as exc:  # an adapter bug must end its own job, not the service
            log.exception("sync of %s crashed", job.supermarket)
            job.status, job.error = JobStatus.FAILED, f"{type(exc).__name__}: {exc}"
        finally:
            job.requests = client.requests
            await client.aclose()
            writer.close()
            job.finished_at = _now()
            self._active.pop(job.supermarket, None)

        if job.status in (JobStatus.BLOCKED, JobStatus.DISALLOWED):
            self._refused_at[job.supermarket] = job.finished_at
        if job.status is JobStatus.SUCCEEDED and job.limit is None:
            writer.promote(job.supermarket, job.finished_at)
        level = logging.INFO if job.status in (JobStatus.SUCCEEDED, JobStatus.CANCELLED) else logging.WARNING
        log.log(level, "sync %s of %s finished: %s, %d products, %d requests%s", job.id, job.supermarket, job.status,
                job.products, job.requests, f" ({job.error})" if job.error else "",
                extra={**_progress(job), "status": str(job.status), "error": job.error})


def _progress(job: Job) -> dict[str, object]:
    elapsed = ((job.finished_at or _now()) - job.started_at).total_seconds() if job.started_at else 0.0
    return {"products": job.products, "requests": job.requests, "failures": job.failures,
            "elapsed_seconds": round(elapsed, 1)}
