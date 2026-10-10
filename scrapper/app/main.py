import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Query, Request, status

from app.config import get_settings
from app.jobs import AlreadyRunningError, CooldownError, JobManager
from app.models import Job, ProductPage, SupermarketInfo
from app.scrapers import SCRAPERS
from app.store import SnapshotReader

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    reader = SnapshotReader(settings.data_dir)
    app.state.reader = reader
    app.state.jobs = JobManager(settings, reader)
    yield
    await app.state.jobs.shutdown()


app = FastAPI(
    title="fdiet scrapper",
    description="Polite catalogue sync for Spanish supermarkets. Start a sync, poll its job, read the snapshot.",
    version="0.1.0",
    lifespan=lifespan,
)


def jobs(request: Request) -> JobManager:
    return request.app.state.jobs


def reader(request: Request) -> SnapshotReader:
    return request.app.state.reader


def known(supermarket: str) -> str:
    if supermarket not in SCRAPERS:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"unknown supermarket '{supermarket}'; one of {sorted(SCRAPERS)}")
    return supermarket


Jobs = Annotated[JobManager, Depends(jobs)]
Reader = Annotated[SnapshotReader, Depends(reader)]
Supermarket = Annotated[str, Depends(known)]


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/supermarkets")
def supermarkets(snapshots: Reader) -> list[SupermarketInfo]:
    infos = []
    for scraper in SCRAPERS.values():
        meta = snapshots.meta(scraper.key)
        infos.append(
            SupermarketInfo(
                key=scraper.key,
                name=scraper.name,
                homepage=scraper.homepage,
                method=scraper.method,
                notes=scraper.notes,
                last_sync=meta.synced_at if meta else None,
                last_sync_products=meta.products if meta else None,
            )
        )
    return infos


def _start(manager: JobManager, supermarket: str, limit: int | None, force: bool) -> Job:
    try:
        return manager.start(supermarket, limit=limit, force=force)
    except AlreadyRunningError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, str(exc)) from exc
    except CooldownError as exc:
        wait = max(0, int((exc.retry_at - datetime.now(timezone.utc)).total_seconds()))
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, str(exc), headers={"Retry-After": str(wait)}) from exc


@app.post("/sync/{supermarket}", status_code=status.HTTP_202_ACCEPTED)
async def sync_one(
    supermarket: Supermarket,
    manager: Jobs,
    limit: Annotated[int | None, Query(ge=1, description="Sample run: stop after N products; not promoted to latest")] = None,
    force: Annotated[bool, Query(description="Ignore the cooldown since the last sync")] = False,
) -> Job:
    return _start(manager, supermarket, limit, force)


@app.post("/sync", status_code=status.HTTP_202_ACCEPTED)
async def sync_all(manager: Jobs, force: bool = False) -> dict[str, Job | str]:
    """Starts every supermarket that is neither running nor cooling down. Each runs against its own host."""
    started: dict[str, Job | str] = {}
    for key in SCRAPERS:
        try:
            started[key] = manager.start(key, force=force)
        except (AlreadyRunningError, CooldownError) as exc:
            started[key] = str(exc)
    return started


@app.get("/jobs")
def list_jobs(manager: Jobs) -> list[Job]:
    return manager.list()


@app.get("/jobs/{job_id}")
def get_job(job_id: str, manager: Jobs) -> Job:
    job = manager.get(job_id)
    if job is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"no job {job_id}")
    return job


@app.post("/jobs/{job_id}/cancel")
async def cancel_job(job_id: str, manager: Jobs) -> Job:
    job = await manager.cancel(job_id)
    if job is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"no job {job_id}")
    return job


@app.get("/products/{supermarket}")
def products(
    supermarket: Supermarket,
    snapshots: Reader,
    q: Annotated[str | None, Query(description="Case-insensitive substring of the name")] = None,
    page: Annotated[int, Query(ge=0)] = 0,
    size: Annotated[int, Query(ge=1, le=1000)] = 100,
) -> ProductPage:
    items = snapshots.products(supermarket)
    if q:
        needle = q.casefold()
        items = [p for p in items if needle in p.name.casefold()]
    meta = snapshots.meta(supermarket)
    return ProductPage(
        supermarket=supermarket,
        synced_at=meta.synced_at if meta else None,
        total=len(items),
        page=page,
        size=size,
        items=items[page * size : (page + 1) * size],
    )
