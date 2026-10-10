"""Shared, pure parsing helpers. No network here, so every function is testable on a string."""

import gzip
import html
import json
import re
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from typing import Any

from bs4 import BeautifulSoup
from defusedxml import ElementTree

from app.models import NutrientValue

_SITEMAP_NS = "{http://www.sitemaps.org/schemas/sitemap/0.9}"
_IMAGE_NS = "{http://www.google.com/schemas/sitemap-image/1.1}"
_NUMBER = re.compile(r"^\s*(-?\d+(?:[.,]\d+)?)\s*(.*?)\s*$")


@dataclass(frozen=True)
class SitemapEntry:
    loc: str
    has_image: bool = False


@dataclass(frozen=True)
class Sitemap:
    is_index: bool
    entries: list[SitemapEntry]


def parse_sitemap(body: bytes) -> Sitemap:
    """A <urlset> or a <sitemapindex>, gzipped or not. defusedxml: the file comes from outside."""
    if body[:2] == b"\x1f\x8b":
        body = gzip.decompress(body)
    root = ElementTree.fromstring(body)
    is_index = root.tag.endswith("sitemapindex")
    child_tag = "sitemap" if is_index else "url"
    entries = []
    for node in root.iter(f"{_SITEMAP_NS}{child_tag}"):
        loc = node.findtext(f"{_SITEMAP_NS}loc")
        if loc:
            entries.append(SitemapEntry(loc.strip(), node.find(f"{_IMAGE_NS}image") is not None))
    return Sitemap(is_index, entries)


def to_decimal(value: Any) -> Decimal | None:
    if value is None or value == "":
        return None
    try:
        return Decimal(str(value).replace(",", ".").strip())
    except InvalidOperation:
        return None


def parse_nutrient(name: str, text: str) -> NutrientValue:
    """'154 Kcal' -> 154.0, 'Kcal'. Anything not a plain number keeps value None."""
    raw = " ".join(text.split())
    match = _NUMBER.match(raw)
    if not match:
        return NutrientValue(name=name.strip(), value=None, unit=None, raw=raw)
    unit = match.group(2) or None
    return NutrientValue(name=name.strip(), value=float(match.group(1).replace(",", ".")), unit=unit, raw=raw)


def json_ld_objects(page: str) -> list[dict[str, Any]]:
    """Every JSON-LD object on the page, @graph flattened. Broken blocks are skipped."""
    soup = BeautifulSoup(page, "html.parser")
    found: list[dict[str, Any]] = []
    for script in soup.find_all("script", type="application/ld+json"):
        try:
            data = json.loads(script.string or "")
        except json.JSONDecodeError:
            continue
        stack = data if isinstance(data, list) else [data]
        while stack:
            item = stack.pop()
            if isinstance(item, list):
                stack.extend(item)
            elif isinstance(item, dict):
                found.append(item)
                if isinstance(item.get("@graph"), list):
                    stack.extend(item["@graph"])
    return found


def _has_type(obj: dict[str, Any], wanted: str) -> bool:
    kind = obj.get("@type")
    return kind == wanted or (isinstance(kind, list) and wanted in kind)


def json_ld_product(page: str) -> dict[str, Any] | None:
    return next((obj for obj in json_ld_objects(page) if _has_type(obj, "Product")), None)


def first_text(value: Any, key: str = "name") -> str | None:
    """schema.org lets most fields be a string, an object, or a list of either."""
    if isinstance(value, list):
        value = value[0] if value else None
    if isinstance(value, dict):
        value = value.get(key) or value.get("url")
    return str(value).strip() if value else None


def unescape_json_attribute(value: str) -> Any:
    return json.loads(html.unescape(value))


class ParseError(ValueError):
    """The page arrived but does not hold what the adapter reads."""


# What an item parser may raise on a page that does not look as expected.
ITEM_PARSE_ERRORS = (ValueError, KeyError, TypeError, IndexError, AttributeError)
