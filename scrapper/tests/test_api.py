import asyncio
import html
import json

import httpx
import pytest

from app.jobs import CooldownError, JobManager
from app.models import JobStatus
from app.store import SnapshotReader
from tests.conftest import routes

OFFERS = "https://www.lidl.es/c/ofertas"


def tile(product_id: int, title: str) -> str:
    data = {
        "productId": product_id,
        "fullTitle": title,
        "price": {"price": 1.0},
        "keyfacts": {"wonCategoryPrimary": "Mundos de necesidad/Comida y cerca de la comida/Panadería"},
    }
    return f'<div data-grid-data="{html.escape(json.dumps(data), quote=True)}"></div>'


@pytest.fixture
def lidl_settings(settings):
    settings.lidl_offer_pages = [OFFERS]
    return settings


async def wait_for(manager: JobManager, job_id: str):
    for _ in range(200):
        job = manager.get(job_id)
        if job.finished_at:
            return job
        await asyncio.sleep(0.01)
    raise AssertionError("job did not finish")


async def test_a_sync_writes_promotes_and_then_cools_down(lidl_settings):
    page = httpx.Response(200, text=tile(1, "Pan") + tile(1, "Pan") + tile(2, "Barra"))
    reader = SnapshotReader(lidl_settings.data_dir)
    manager = JobManager(lidl_settings, reader, routes({OFFERS: page}))

    job = await wait_for(manager, manager.start("lidl").id)
    assert job.status is JobStatus.SUCCEEDED
    assert job.products == 2  # the duplicate tile is written once
    assert [p.name for p in reader.products("lidl")] == ["Pan", "Barra"]
    assert reader.meta("lidl").products == 2

    with pytest.raises(CooldownError):
        manager.start("lidl")
    forced = await wait_for(manager, manager.start("lidl", force=True).id)
    assert forced.status is JobStatus.SUCCEEDED


async def test_a_sample_run_is_not_promoted(lidl_settings):
    reader = SnapshotReader(lidl_settings.data_dir)
    manager = JobManager(lidl_settings, reader, routes({OFFERS: httpx.Response(200, text=tile(1, "Pan") + tile(2, "Barra"))}))
    job = await wait_for(manager, manager.start("lidl", limit=1).id)
    assert job.status is JobStatus.SUCCEEDED and job.products == 1
    assert reader.meta("lidl") is None


async def test_a_blocked_site_stops_at_once_and_cools_down(lidl_settings):
    transport = routes({"https://www.lidl.es/robots.txt": httpx.Response(403, text="Access Denied")})
    manager = JobManager(lidl_settings, SnapshotReader(lidl_settings.data_dir), transport)
    job = await wait_for(manager, manager.start("lidl").id)
    assert job.status is JobStatus.BLOCKED
    assert transport.calls == ["https://www.lidl.es/robots.txt"]
    with pytest.raises(CooldownError):
        manager.start("lidl")


async def test_robots_disallowing_the_api_is_reported_as_disallowed(settings):
    transport = routes({}, default_robots="User-agent: *\nAllow: /product\nDisallow: /api\n")
    manager = JobManager(settings, SnapshotReader(settings.data_dir), transport)
    job = await wait_for(manager, manager.start("mercadona").id)
    assert job.status is JobStatus.DISALLOWED
    assert job.requests == 1  # robots.txt only


async def test_a_run_without_products_keeps_the_previous_snapshot(lidl_settings):
    reader = SnapshotReader(lidl_settings.data_dir)
    manager = JobManager(lidl_settings, reader, routes({OFFERS: httpx.Response(200, text=tile(1, "Pan"))}))
    await wait_for(manager, manager.start("lidl").id)

    empty = JobManager(lidl_settings, reader, routes({OFFERS: httpx.Response(200, text="<html></html>")}))
    job = await wait_for(empty, empty.start("lidl", force=True).id)
    assert job.status is JobStatus.FAILED
    assert [p.name for p in reader.products("lidl")] == ["Pan"]


def test_http_endpoints(monkeypatch, tmp_path):
    from fastapi.testclient import TestClient

    monkeypatch.setenv("SCRAPER_DATA_DIR", str(tmp_path))
    from app.config import get_settings

    get_settings.cache_clear()
    from app.main import app

    with TestClient(app) as client:
        assert client.get("/health").json() == {"status": "ok"}
        keys = {s["key"] for s in client.get("/supermarkets").json()}
        assert keys == {"carrefour", "mercadona", "aldi", "dia", "eroski", "lupa", "lidl"}
        assert client.post("/sync/nowhere").status_code == 404
        page = client.get("/products/aldi").json()
        assert (page["total"], page["items"]) == (0, [])
    get_settings.cache_clear()
