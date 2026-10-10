"""What a food of fdiet is searched by, and what a search answer becomes. Pure: no network.

Two kinds of answer, and they are not equal:

- **`ean`** - the product carries the food's own barcode. The same product, sold there.
- **`name`** - the product's name shares enough words with the food's. A *candidate*, ranked
  by OpenSearch's score; like every name similarity in fdiet it is offered and never decides.

Each search collapses on the supermarket, so every supermarket gets its own best few instead
of one shop with forty brands of milk crowding out the other six.
"""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from app.foodsync.indices import MATCH_FIELDS

FOOD_ITEM = "food_item"
COMPOSITION = "composition"
# At least this share of a name's words must appear in a product's name (all of them up to two).
NAME_MINIMUM_SHOULD_MATCH = "2<75%"
# A barcode outranks any name similarity.
EAN_BOOST = 10.0
GTIN_LENGTHS = frozenset({8, 12, 13, 14})


def normalize_gtin(value: str | None) -> str | None:
    """A barcode as GTIN-14 (digits, zero-padded), or None when it is not one.

    An EAN-8, a UPC-12 and an EAN-13 of the same product only differ by leading zeros once
    padded, which is how the GS1 standard compares them.
    """
    if not value:
        return None
    digits = "".join(ch for ch in value if ch.isdigit())
    return digits.zfill(14) if len(digits) in GTIN_LENGTHS else None


@dataclass(frozen=True)
class FoodQuery:
    food_type: str
    food_id: int
    name: str | None
    names: list[str] = field(default_factory=list)
    brand: str | None = None
    ean: str | None = None
    source: str | None = None


def food_item_query(dto: dict[str, Any]) -> FoodQuery | None:
    """A branded product of fdiet (`GET /api/food`): its barcode first, its commercial name second."""
    name = (dto.get("commercialName") or "").strip() or None
    ean = normalize_gtin(dto.get("ean"))
    if not name and not ean:
        return None
    return FoodQuery(FOOD_ITEM, dto["id"], name, [name] if name else [], (dto.get("brand") or "").strip() or None, ean)


def composition_query(dto: dict[str, Any]) -> FoodQuery | None:
    """A CIQUAL / BLS food (`GET /api/composition`), by its Spanish name and aliases only.

    Supermarkets name products in Spanish; a food the crosswalk has not named in Spanish yet
    would be searched by an English or French name and meet nothing worth offering.
    """
    name_es = (dto.get("nameEs") or "").strip()
    if not name_es:
        return None
    names = [name_es, *(alias.strip() for alias in dto.get("aliases") or [] if alias and alias.strip())]
    return FoodQuery(COMPOSITION, dto["id"], name_es, names, source=dto.get("source"))


def build_query(food: FoodQuery, supermarkets: int, per_supermarket: int) -> dict[str, Any]:
    should: list[dict[str, Any]] = []
    if food.ean:
        should.append({"term": {"ean": {"value": food.ean, "boost": EAN_BOOST}}})
    if food.names:
        by_name: dict[str, Any] = {"bool": {
            "should": [{"match": {"name": {"query": name, "minimum_should_match": NAME_MINIMUM_SHOULD_MATCH}}}
                       for name in food.names],
            "minimum_should_match": 1,
        }}
        if food.brand:
            # The brand only reorders products that already match by name.
            by_name = {"bool": {"must": [by_name], "should": [{"match": {"brand": food.brand}}]}}
        should.append(by_name)
    return {
        "size": supermarkets,
        "_source": False,
        "query": {"bool": {"should": should, "minimum_should_match": 1}},
        "collapse": {"field": "supermarket",
                     "inner_hits": {"name": "best", "size": per_supermarket, "_source": MATCH_FIELDS}},
    }


def match_document(food: FoodQuery, response: dict[str, Any], sync_id: str, matched_at: datetime) -> dict[str, Any]:
    matches = []
    for group in response["hits"]["hits"]:
        for hit in group["inner_hits"]["best"]["hits"]["hits"]:
            product = hit["_source"]
            same_code = food.ean is not None and product.get("ean") == food.ean
            matches.append({**product, "match_type": "ean" if same_code else "name", "score": hit["_score"]})
    matches.sort(key=lambda match: (match["match_type"] != "ean", -match["score"]))
    return {
        "food_type": food.food_type,
        "food_id": food.food_id,
        "name": food.name,
        "brand": food.brand,
        "ean": food.ean,
        "source": food.source,
        "match_count": len(matches),
        "best_score": matches[0]["score"] if matches else None,
        "ean_matched": any(match["match_type"] == "ean" for match in matches),
        "supermarkets": sorted({match["supermarket"] for match in matches}),
        "matches": matches,
        "sync_id": sync_id,
        "matched_at": matched_at.isoformat(),
    }


def document_id(food: FoodQuery) -> str:
    return f"{food.food_type}:{food.food_id}"
