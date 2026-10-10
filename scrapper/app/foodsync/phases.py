"""The two OpenSearch phases of a food sync. Each writes a fresh index and moves the alias onto
it only once it is complete, so a reader never sees a half-built one. Progress is written into
the run's model as it goes; `on_progress` persists it."""

import logging
from collections.abc import Callable, Iterator
from datetime import datetime, timezone
from itertools import batched
from typing import Any

from app.config import Settings
from app.foodsync import indices
from app.foodsync.fdiet_api import COMPOSITION, FOOD_ITEMS, FdietApi
from app.foodsync.matching import (FoodQuery, build_query, composition_query, document_id, food_item_query,
                                   match_document, normalize_gtin)
from app.foodsync.opensearch import SearchClient, SearchError
from app.models import IngestProgress, MatchCounts, MatchProgress, Product
from app.store import SnapshotReader

log = logging.getLogger(__name__)
# The match phase logs its progress every this many pages of the fdiet API.
LOG_EVERY_PAGES = 25


def _now() -> datetime:
    return datetime.now(timezone.utc)


def product_document(product: Product, synced_at: datetime) -> tuple[str, dict[str, Any]]:
    doc = product.model_dump(mode="json")
    doc["ean_published"] = product.ean
    doc["ean"] = normalize_gtin(product.ean)
    doc["synced_at"] = synced_at.isoformat()
    return f"{product.supermarket}:{product.source_id}", doc


async def ingest(settings: Settings, search: SearchClient, reader: SnapshotReader, supermarkets: list[str],
                 progress: IngestProgress, on_progress: Callable[[], None]) -> None:
    """Every supermarket's latest complete snapshot into a new products index."""
    progress.started_at = _now()
    alias = indices.products_alias(settings)
    progress.index = indices.timestamped(alias, progress.started_at)
    await search.create_index(progress.index, indices.PRODUCTS)
    try:
        for key in supermarkets:
            meta = reader.meta(key)
            if meta is None:
                progress.without_snapshot.append(key)
                log.warning("%s has no complete snapshot; nothing of it is indexed", key, extra={"supermarket": key})
                continue
            docs: Iterator[tuple[str, dict[str, Any]]] = (product_document(p, meta.synced_at) for p in reader.iter_products(key))
            for chunk in batched(docs, settings.opensearch_bulk_size):
                written = await search.bulk_index(progress.index, chunk)
                progress.products += written
                progress.by_supermarket[key] = progress.by_supermarket.get(key, 0) + written
                on_progress()
            log.info("indexed %d products of %s (snapshot of %s)", progress.by_supermarket.get(key, 0), key,
                     meta.synced_at.isoformat(), extra={"supermarket": key, "products": progress.by_supermarket.get(key, 0)})
        if progress.products == 0:
            raise SearchError("no supermarket has a complete snapshot; run a scrape first")
        await search.refresh(progress.index)
        await search.point_alias(alias, progress.index)
    except BaseException:
        await _discard(search, progress.index)
        raise
    progress.finished_at = _now()


async def match(settings: Settings, search: SearchClient, api: FdietApi, sync_id: str, supermarkets: int,
                progress: MatchProgress, on_progress: Callable[[], None]) -> None:
    """Every food of the fdiet backend against the products index, into a new matches index."""
    progress.started_at = _now()
    alias = indices.matches_alias(settings)
    progress.index = indices.timestamped(alias, progress.started_at)
    await search.create_index(progress.index, indices.FOOD_MATCHES)
    try:
        halves = ((FOOD_ITEMS, food_item_query, progress.food_items), (COMPOSITION, composition_query, progress.composition))
        for path, to_query, counts in halves:
            pages = 0
            async for total, content in api.pages(path):
                counts.total = total
                pages += 1
                queries = [query for dto in content if (query := to_query(dto)) is not None]
                counts.skipped += len(content) - len(queries)
                for chunk in batched(queries, settings.opensearch_msearch_size):
                    await _match_chunk(settings, search, list(chunk), sync_id, supermarkets, progress.index, counts)
                on_progress()
                if pages % LOG_EVERY_PAGES == 0:
                    log.info("%s: %d of %d foods", path, counts.processed + counts.skipped, total, extra=_counts(path, counts))
            log.info("%s done", path, extra=_counts(path, counts))
        await search.refresh(progress.index)
        await search.point_alias(alias, progress.index)
    except BaseException:
        await _discard(search, progress.index)
        raise
    progress.finished_at = _now()


async def _match_chunk(settings: Settings, search: SearchClient, foods: list[FoodQuery], sync_id: str,
                       supermarkets: int, index: str, counts: MatchCounts) -> None:
    per_supermarket = settings.food_sync_matches_per_supermarket
    responses = await search.msearch(indices.products_alias(settings),
                                     [build_query(food, supermarkets, per_supermarket) for food in foods])
    matched_at = _now()
    docs = [match_document(food, response, sync_id, matched_at) for food, response in zip(foods, responses, strict=True)]
    await search.bulk_index(index, ((document_id(food), doc) for food, doc in zip(foods, docs, strict=True)))
    for doc in docs:
        if doc["ean_matched"]:
            counts.ean_matched += 1
        elif doc["match_count"]:
            counts.name_matched += 1
        else:
            counts.unmatched += 1
    counts.processed += len(foods)


async def _discard(search: SearchClient, index: str) -> None:
    """Drops an index a failed phase left half-written. The alias never pointed at it."""
    try:
        await search.delete_index(index)
    except SearchError as exc:
        log.warning("could not delete the half-written index %s: %s", index, exc)


def _counts(path: str, counts: MatchCounts) -> dict[str, object]:
    return {"source_path": path, **counts.model_dump()}
