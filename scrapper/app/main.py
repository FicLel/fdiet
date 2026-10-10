from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Annotated, Any

from fastapi import Depends, FastAPI, HTTPException, Query, Request, status

from app import logs
from app.config import get_settings
from app.foodsync import indices
from app.foodsync.fdiet_api import FdietApi
from app.foodsync.manager import FoodSyncManager, FoodSyncRunningError
from app.foodsync.matching import COMPOSITION, FOOD_ITEM, normalize_gtin
from app.foodsync.opensearch import SearchClient, SearchError
from app.jobs import AlreadyRunningError, CooldownError, JobManager
from app.models import FoodSync, FoodSyncOptions, Job, ProductPage, SupermarketInfo
from app.scrapers import SCRAPERS
from app.store import SnapshotReader

logs.configure(get_settings())


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    reader = SnapshotReader(settings.data_dir)
    search = SearchClient(settings.opensearch_url, settings.opensearch_timeout_seconds)
    api = FdietApi(settings.fdiet_api_url, settings.fdiet_api_page_size)
    app.state.reader = reader
    app.state.jobs = JobManager(settings, reader)
    app.state.search = search
    app.state.food_syncs = FoodSyncManager(settings, app.state.jobs, reader, search, api)
    yield
    await app.state.food_syncs.shutdown()
    await app.state.jobs.shutdown()
    await search.aclose()
    await api.aclose()


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


def food_syncs(request: Request) -> FoodSyncManager:
    return request.app.state.food_syncs


def search_client(request: Request) -> SearchClient:
    return request.app.state.search


def known(supermarket: str) -> str:
    if supermarket not in SCRAPERS:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"unknown supermarket '{supermarket}'; one of {sorted(SCRAPERS)}")
    return supermarket


Jobs = Annotated[JobManager, Depends(jobs)]
Reader = Annotated[SnapshotReader, Depends(reader)]
FoodSyncs = Annotated[FoodSyncManager, Depends(food_syncs)]
Search = Annotated[SearchClient, Depends(search_client)]
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


# --- The food sync: scrape -> OpenSearch -> match fdiet's foods -------------------------


@app.post("/food-sync", status_code=status.HTTP_202_ACCEPTED, tags=["food sync"])
async def start_food_sync(
    manager: FoodSyncs,
    scrape: Annotated[bool, Query(description="Scrape first; false indexes the snapshots already on disk")] = True,
    force: Annotated[bool, Query(description="Scrape even inside a supermarket's cooldown")] = False,
    supermarket: Annotated[list[str] | None, Query(description="Supermarkets to scrape (repeatable); default all")] = None,
) -> FoodSync:
    """Fire and forget: answers at once with the run; poll `GET /food-sync/{id}` for its status.
    One food sync at a time (409 while one runs)."""
    chosen = supermarket or list(SCRAPERS)
    for key in chosen:
        known(key)
    try:
        return manager.start(FoodSyncOptions(scrape=scrape, force=force, supermarkets=chosen))
    except FoodSyncRunningError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, str(exc)) from exc


@app.get("/food-sync", tags=["food sync"])
def list_food_syncs(manager: FoodSyncs) -> list[FoodSync]:
    return manager.list()


@app.get("/food-sync/latest", tags=["food sync"])
def latest_food_sync(manager: FoodSyncs) -> FoodSync:
    run = manager.latest()
    if run is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "no food sync has run yet")
    return run


@app.get("/food-sync/{sync_id}", tags=["food sync"])
def get_food_sync(sync_id: str, manager: FoodSyncs) -> FoodSync:
    run = manager.get(sync_id)
    if run is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"no food sync {sync_id}")
    return run


@app.post("/food-sync/{sync_id}/cancel", tags=["food sync"])
async def cancel_food_sync(sync_id: str, manager: FoodSyncs) -> FoodSync:
    """Stops the run and the scrape jobs it started. The aliases stay on the last complete indices."""
    run = await manager.cancel(sync_id)
    if run is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"no food sync {sync_id}")
    return run


def _search_failed(exc: SearchError) -> HTTPException:
    return HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, str(exc))


@app.get("/search/products", tags=["food sync"])
async def search_products(
    search: Search,
    q: Annotated[str | None, Query(description="Words of the product name (accents and plurals ignored)")] = None,
    ean: Annotated[str | None, Query(description="A barcode: EAN-8, EAN-13 or UPC-12")] = None,
    supermarket: Annotated[str | None, Query()] = None,
    size: Annotated[int, Query(ge=1, le=100)] = 20,
) -> dict[str, Any]:
    """The indexed products of every supermarket, read from OpenSearch instead of the sites."""
    must: list[dict[str, Any]] = []
    if q:
        must.append({"match": {"name": {"query": q, "operator": "and"}}})
    if ean:
        must.append({"term": {"ean": normalize_gtin(ean) or ean}})
    if supermarket:
        must.append({"term": {"supermarket": known(supermarket)}})
    body = {"size": size, "query": {"bool": {"must": must or [{"match_all": {}}]}}}
    try:
        answer = await search.search(indices.products_alias(get_settings()), body)
    except SearchError as exc:
        raise _search_failed(exc) from exc
    return {"total": answer["hits"]["total"]["value"],
            "items": [{**hit["_source"], "score": hit["_score"]} for hit in answer["hits"]["hits"]]}


@app.get("/food-matches/{food_type}/{food_id}", tags=["food sync"])
async def food_matches(food_type: str, food_id: int, search: Search) -> dict[str, Any]:
    """Where one fdiet food is sold, as the last food sync found it. `food_type` is
    `food_item` (GET /api/food) or `composition` (GET /api/composition)."""
    if food_type not in (FOOD_ITEM, COMPOSITION):
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"food_type is {FOOD_ITEM} or {COMPOSITION}")
    try:
        doc = await search.get(indices.matches_alias(get_settings()), f"{food_type}:{food_id}")
    except SearchError as exc:
        raise _search_failed(exc) from exc
    if doc is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"{food_type} {food_id} was not matched by the last food sync")
    return doc
