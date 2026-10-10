import asyncio
import json
from datetime import datetime, timezone

import httpx
import pytest

from app.foodsync.fdiet_api import FdietApi
from app.foodsync.manager import FoodSyncManager, FoodSyncRunningError
from app.foodsync.matching import (FoodQuery, build_query, composition_query, food_item_query, match_document,
                                   normalize_gtin)
from app.foodsync.opensearch import SearchClient
from app.jobs import JobManager
from app.models import FoodSync, FoodSyncOptions, FoodSyncStatus, Product
from app.store import SnapshotReader, SnapshotWriter

NOW = datetime(2026, 10, 10, tzinfo=timezone.utc)


# --- pure ----------------------------------------------------------------------------------


def test_a_barcode_is_compared_as_gtin_14():
    assert normalize_gtin("8480000123456") == "08480000123456"
    assert normalize_gtin("012345678905") == "00012345678905"  # UPC-12
    assert normalize_gtin("8480 0001 2345 6") == "08480000123456"
    assert normalize_gtin("12345") is None
    assert normalize_gtin(None) is None


def test_a_branded_food_is_searched_by_barcode_and_name_and_a_composition_food_by_spanish_names():
    item = food_item_query({"id": 7, "commercialName": "LECHE ENTERA", "brand": "PASCUAL", "ean": "8410100000001"})
    assert item.ean == "08410100000001" and item.names == ["LECHE ENTERA"] and item.brand == "PASCUAL"
    assert food_item_query({"id": 8, "commercialName": " ", "ean": None}) is None

    food = composition_query({"id": 3, "nameEs": "Lechuga", "aliases": ["lechuga iceberg", ""], "source": "CIQUAL"})
    assert food.names == ["Lechuga", "lechuga iceberg"] and food.source == "CIQUAL"
    assert composition_query({"id": 4, "nameEs": None, "nameEn": "Lettuce"}) is None


def test_the_query_collapses_on_the_supermarket_and_puts_the_barcode_first():
    query = build_query(FoodQuery("food_item", 1, "Leche", ["Leche"], "Pascual", "08410100000001"), 7, 3)
    assert query["size"] == 7
    assert query["collapse"]["field"] == "supermarket"
    assert query["collapse"]["inner_hits"]["size"] == 3
    should = query["query"]["bool"]["should"]
    assert should[0] == {"term": {"ean": {"value": "08410100000001", "boost": 10.0}}}
    assert should[1]["bool"]["should"] == [{"match": {"brand": "Pascual"}}]


def test_an_answer_becomes_one_document_with_the_barcode_match_first():
    food = FoodQuery("food_item", 1, "Leche", ["Leche"], None, "08410100000001")

    def hit(supermarket, name, ean, score):
        return {"_source": {"supermarket": supermarket, "name": name, "ean": ean}, "_score": score}

    response = {"hits": {"hits": [
        {"inner_hits": {"best": {"hits": {"hits": [hit("eroski", "Leche entera", None, 9.0)]}}}},
        {"inner_hits": {"best": {"hits": {"hits": [hit("mercadona", "Leche entera Hacendado", "08410100000001", 3.0)]}}}},
    ]}}
    doc = match_document(food, response, "s1", NOW)
    assert [m["match_type"] for m in doc["matches"]] == ["ean", "name"]
    assert doc["ean_matched"] is True and doc["match_count"] == 2
    assert doc["supermarkets"] == ["eroski", "mercadona"]
    assert doc["matched_at"] == NOW.isoformat()


# --- a fake OpenSearch and a fake fdiet backend ----------------------------------------------


class FakeOpenSearch:
    """Just enough of the REST API: indices, aliases, _bulk, and an _msearch that answers a
    product whose EAN equals the term or whose name contains every word of a match clause."""

    def __init__(self) -> None:
        self.indices: dict[str, dict[str, dict]] = {}
        self.aliases: dict[str, str] = {}

    def resolve(self, name: str) -> str:
        return self.aliases.get(name, name)

    def __call__(self, request: httpx.Request) -> httpx.Response:
        path, method = request.url.path, request.method
        parts = [p for p in path.split("/") if p]
        if path == "/":
            return httpx.Response(200, json={"version": {"number": "2.19.1"}})
        if path == "/_bulk":
            lines = request.content.decode().splitlines()
            for action, doc in zip(lines[::2], lines[1::2]):
                meta = json.loads(action)["index"]
                self.indices[meta["_index"]][meta["_id"]] = json.loads(doc)
            return httpx.Response(200, json={"errors": False, "items": []})
        if parts[0] == "_alias":
            index = self.aliases.get(parts[1])
            return httpx.Response(200, json={index: {}}) if index else httpx.Response(404, json={})
        if path == "/_aliases":
            for action in json.loads(request.content)["actions"]:
                if "add" in action:
                    self.aliases[action["add"]["alias"]] = action["add"]["index"]
            return httpx.Response(200, json={"acknowledged": True})
        if len(parts) == 1 and method == "PUT":
            self.indices[parts[0]] = {}
            return httpx.Response(200, json={"acknowledged": True})
        if len(parts) == 1 and method == "DELETE":
            return httpx.Response(200 if self.indices.pop(parts[0], None) is not None else 404, json={})
        if parts[-1] == "_refresh":
            return httpx.Response(200, json={})
        if parts[-1] == "_msearch":
            docs = self.indices[self.resolve(parts[0])]
            lines = request.content.decode().splitlines()
            return httpx.Response(200, json={"responses": [self.answer(docs, json.loads(q)) for q in lines[1::2]]})
        if parts[1] == "_doc":
            doc = self.indices.get(self.resolve(parts[0]), {}).get(parts[2])
            return httpx.Response(200, json={"_source": doc}) if doc else httpx.Response(404, json={})
        return httpx.Response(400, json={"error": f"unrouted {method} {path}"})

    @staticmethod
    def answer(docs: dict[str, dict], query: dict) -> dict:
        should = query["query"]["bool"]["should"]
        ean = next((c["term"]["ean"]["value"] for c in should if "term" in c), None)
        names = [m["match"]["name"]["query"].lower().split()
                 for c in should if "bool" in c
                 for m in (c["bool"]["must"][0] if "must" in c["bool"] else c)["bool"]["should"]]
        groups: dict[str, list] = {}
        for doc in docs.values():
            if (ean and doc["ean"] == ean) or any(all(w in doc["name"].lower() for w in words) for words in names):
                groups.setdefault(doc["supermarket"], []).append({"_source": doc, "_score": 1.0})
        return {"hits": {"hits": [{"inner_hits": {"best": {"hits": {"hits": hits}}}} for hits in groups.values()]}}


def fdiet_backend(food: list[dict], composition: list[dict]) -> httpx.MockTransport:
    def handle(request: httpx.Request) -> httpx.Response:
        rows = {"/api/food": food, "/api/composition": composition}[request.url.path]
        page, size = int(request.url.params["page"]), int(request.url.params["size"])
        content = rows[page * size:(page + 1) * size]
        return httpx.Response(200, json={"content": content, "totalElements": len(rows),
                                         "last": (page + 1) * size >= len(rows)})
    return httpx.MockTransport(handle)


def snapshot(settings, supermarket: str, products: list[tuple[str, str, str | None]]) -> None:
    writer = SnapshotWriter(settings.data_dir, supermarket, NOW)
    for source_id, name, ean in products:
        writer.write(Product(supermarket=supermarket, source_id=source_id, name=name, ean=ean, scraped_at=NOW))
    writer.close()
    writer.promote(supermarket, NOW)


async def finished(manager: FoodSyncManager, sync_id: str):
    for _ in range(500):
        run = manager.get(sync_id)
        if run.finished_at:
            return run
        await asyncio.sleep(0.01)
    raise AssertionError("food sync did not finish")


@pytest.fixture
def opensearch():
    return FakeOpenSearch()


@pytest.fixture
def manager(settings, opensearch):
    settings.fdiet_api_page_size = 2
    settings.opensearch_msearch_size = 2
    reader = SnapshotReader(settings.data_dir)
    search = SearchClient("http://opensearch", 5, httpx.MockTransport(opensearch))
    api = FdietApi("http://fdiet", settings.fdiet_api_page_size, transport=fdiet_backend(
        food=[{"id": 1, "commercialName": "LECHE ENTERA", "ean": "8410100000001"},
              {"id": 2, "commercialName": "TURRON DURO", "ean": None},
              {"id": 3, "commercialName": "", "ean": None}],
        composition=[{"id": 10, "nameEs": "Lechuga", "aliases": [], "source": "CIQUAL"},
                     {"id": 11, "nameEs": None, "nameEn": "Kale"}]))
    return FoodSyncManager(settings, JobManager(settings, reader), reader, search, api)


async def test_a_food_sync_indexes_the_snapshots_and_matches_every_food(settings, manager, opensearch):
    snapshot(settings, "mercadona", [("m1", "Leche entera Hacendado", "8410100000001"), ("m2", "Lechuga iceberg", None)])
    snapshot(settings, "eroski", [("e1", "Leche entera Eroski", None)])

    run = await finished(manager, manager.start(FoodSyncOptions(scrape=False, force=False, supermarkets=[])).id)

    assert run.status is FoodSyncStatus.SUCCEEDED, run.error
    assert run.ingest.products == 3 and run.ingest.by_supermarket == {"mercadona": 2, "eroski": 1}
    assert "aldi" in run.ingest.without_snapshot
    items, composition = run.match.food_items, run.match.composition
    assert (items.total, items.processed, items.skipped) == (3, 2, 1)
    assert (items.ean_matched, items.name_matched, items.unmatched) == (1, 0, 1)
    assert (composition.processed, composition.skipped, composition.name_matched) == (1, 1, 1)

    matches = opensearch.indices[opensearch.aliases["fdiet-food-matches"]]
    milk = matches["food_item:1"]
    assert milk["ean_matched"] and milk["matches"][0]["source_id"] == "m1"
    assert {m["supermarket"] for m in milk["matches"]} == {"mercadona", "eroski"}
    assert matches["composition:10"]["matches"][0]["name"] == "Lechuga iceberg"

    # The status was written to disk and is read back by the next process.
    again = FoodSyncManager(settings, manager.jobs, manager.reader, manager.search, manager.api)
    assert again.get(run.id).status is FoodSyncStatus.SUCCEEDED


async def test_a_second_food_sync_moves_the_alias_and_drops_the_old_index(settings, manager, opensearch):
    snapshot(settings, "eroski", [("e1", "Leche entera Eroski", None)])
    first = await finished(manager, manager.start(FoodSyncOptions(scrape=False, force=False, supermarkets=[])).id)
    second = await finished(manager, manager.start(FoodSyncOptions(scrape=False, force=False, supermarkets=[])).id)
    assert second.status is FoodSyncStatus.SUCCEEDED
    if first.ingest.index != second.ingest.index:  # same second: same name, nothing to drop
        assert first.ingest.index not in opensearch.indices
    assert opensearch.aliases["fdiet-products"] == second.ingest.index


async def test_without_any_snapshot_the_run_fails_and_leaves_no_index_behind(manager, opensearch):
    run = await finished(manager, manager.start(FoodSyncOptions(scrape=False, force=False, supermarkets=[])).id)
    assert run.status is FoodSyncStatus.FAILED and "no supermarket has a complete snapshot" in run.error
    assert opensearch.indices == {} and opensearch.aliases == {}


async def test_one_food_sync_at_a_time(settings, manager):
    snapshot(settings, "eroski", [("e1", "Leche", None)])
    run = manager.start(FoodSyncOptions(scrape=False, force=False, supermarkets=[]))
    with pytest.raises(FoodSyncRunningError):
        manager.start(FoodSyncOptions(scrape=False, force=False, supermarkets=[]))
    await finished(manager, run.id)


async def test_an_unreachable_opensearch_fails_the_run_before_any_scrape(settings, manager):
    def down(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused")

    manager.search = SearchClient("http://opensearch", 5, httpx.MockTransport(down))
    run = await finished(manager, manager.start(FoodSyncOptions(scrape=True, force=False, supermarkets=["lidl"])).id)
    assert run.status is FoodSyncStatus.FAILED and run.phase == "preflight"
    assert run.scrape == {} and manager.jobs.list() == []


def test_a_run_found_running_on_startup_is_marked_interrupted(settings, manager):
    left = FoodSync(id="killed", status=FoodSyncStatus.RUNNING, created_at=NOW,
                    options=FoodSyncOptions(scrape=False, force=False, supermarkets=[]))
    manager._save(left)  # what a killed process leaves on disk
    restarted = FoodSyncManager(settings, manager.jobs, manager.reader, manager.search, manager.api)
    assert restarted.get("killed").status is FoodSyncStatus.INTERRUPTED
    assert restarted.get("killed").finished_at is not None
