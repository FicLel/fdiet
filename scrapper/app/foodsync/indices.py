"""The two indices the food sync writes, and the analyser their names are searched with.

`es_food` folds case and accents and stems Spanish lightly, so `Pechugas de pollo` meets
`pechuga pollo`; stop words go before the accents are folded, because the Spanish list is
written with them (`más`, `él`).
"""

from datetime import datetime
from typing import Any

from app.config import Settings

ANALYSIS: dict[str, Any] = {
    "filter": {
        "es_stop": {"type": "stop", "stopwords": "_spanish_"},
        "es_stem": {"type": "stemmer", "language": "light_spanish"},
    },
    "analyzer": {
        "es_food": {"type": "custom", "tokenizer": "standard", "filter": ["lowercase", "es_stop", "asciifolding", "es_stem"]},
    },
}

_TEXT = {"type": "text", "analyzer": "es_food", "fields": {"raw": {"type": "keyword", "ignore_above": 512}}}
_PRICE = {"type": "scaled_float", "scaling_factor": 100}
_LINK = {"type": "keyword", "index": False}

PRODUCTS: dict[str, Any] = {
    "settings": {"number_of_shards": 1, "number_of_replicas": 0, "analysis": ANALYSIS},
    "mappings": {
        "dynamic": "strict",
        "properties": {
            "supermarket": {"type": "keyword"},
            "source_id": {"type": "keyword"},
            "name": _TEXT,
            "brand": _TEXT,
            # GTIN-14, zero-padded: an EAN-13 and the same code as a UPC-12 are the same product.
            "ean": {"type": "keyword"},
            "ean_published": {"type": "keyword"},
            "url": _LINK,
            "image_url": _LINK,
            "category": {"type": "keyword"},
            "price": _PRICE,
            "price_note": {"type": "keyword"},
            "reference_price": _PRICE,
            "reference_unit": {"type": "keyword"},
            "package": {"type": "keyword"},
            "available": {"type": "boolean"},
            "nutrition_basis": {"type": "keyword"},
            # Kept as published, returned with the product, not searched.
            "nutrition": {"type": "object", "enabled": False},
            "ingredients": {"type": "text", "analyzer": "es_food"},
            "scraped_at": {"type": "date"},
            "synced_at": {"type": "date"},
        },
    },
}

# What a match carries of the product it points at.
MATCH_FIELDS = ["supermarket", "source_id", "name", "brand", "ean", "price", "price_note", "reference_price",
                "reference_unit", "package", "url", "image_url", "category", "synced_at"]

FOOD_MATCHES: dict[str, Any] = {
    "settings": {"number_of_shards": 1, "number_of_replicas": 0, "analysis": ANALYSIS},
    "mappings": {
        "dynamic": "strict",
        "properties": {
            "food_type": {"type": "keyword"},
            "food_id": {"type": "long"},
            "name": _TEXT,
            "brand": {"type": "keyword"},
            "ean": {"type": "keyword"},
            "source": {"type": "keyword"},
            "match_count": {"type": "integer"},
            "best_score": {"type": "float"},
            "ean_matched": {"type": "boolean"},
            "supermarkets": {"type": "keyword"},
            "matches": {
                "type": "nested",
                "properties": {
                    "supermarket": {"type": "keyword"},
                    "source_id": {"type": "keyword"},
                    "match_type": {"type": "keyword"},
                    "score": {"type": "float"},
                    "name": _TEXT,
                    "brand": {"type": "keyword"},
                    "ean": {"type": "keyword"},
                    "price": _PRICE,
                    "price_note": {"type": "keyword"},
                    "reference_price": _PRICE,
                    "reference_unit": {"type": "keyword"},
                    "package": {"type": "keyword"},
                    "url": _LINK,
                    "image_url": _LINK,
                    "category": {"type": "keyword"},
                    "synced_at": {"type": "date"},
                },
            },
            "sync_id": {"type": "keyword"},
            "matched_at": {"type": "date"},
        },
    },
}


def products_alias(settings: Settings) -> str:
    return f"{settings.opensearch_prefix}-products"


def matches_alias(settings: Settings) -> str:
    return f"{settings.opensearch_prefix}-food-matches"


def timestamped(alias: str, at: datetime) -> str:
    return f"{alias}-{at:%Y%m%dt%H%M%S}"
