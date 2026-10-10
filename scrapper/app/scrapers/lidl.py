"""Lidl Spain: the online shop sells no groceries; food appears only in the in-store offers.

Discovery: the offer pages in `SCRAPER_LIDL_OFFER_PAGES` (this week's, next week's).
Data: every product tile carries its full product record in `data-grid-data`: id, title,
category path, price (or the Lidl Plus price), pack size, offer dates. The product sitemap
is not used: it lists the non-food online range.
"""

import re
from collections.abc import AsyncIterator
from datetime import datetime
from typing import Any

from app.models import Product
from app.scrapers.base import Scraper, ScrapeContext
from app.scrapers.parsing import ITEM_PARSE_ERRORS, to_decimal, unescape_json_attribute

BASE = "https://www.lidl.es"
_GRID_DATA = re.compile(r'data-grid-data="([^"]*)"')
_FOOD_SECTIONS = ("Comida y cerca de la comida", "Vino, cerveza y licores")
_NOT_FOOD = ("droguería", "mascotas", "limpieza")


def is_food(category_path: str) -> bool:
    lowered = category_path.lower()
    return any(section in category_path for section in _FOOD_SECTIONS) and not any(word in lowered for word in _NOT_FOOD)


def _price(grid: dict[str, Any]) -> tuple[Any, str | None, str | None]:
    """(price, note, packaging). The shelf price first; the Lidl Plus price only when it is all there is."""
    price = grid.get("price") or {}
    packaging = (price.get("packaging") or {}).get("text")
    if price.get("price") is not None:
        return price["price"], None, packaging
    for offer in grid.get("lidlPlus") or []:
        plus = offer.get("price") or {}
        if plus.get("price") is not None:
            return plus["price"], "Lidl Plus", packaging or (plus.get("packaging") or {}).get("text")
    return None, None, packaging


def parse_offer_page(page: str, scraped_at: datetime) -> list[Product]:
    products = []
    for match in _GRID_DATA.finditer(page):
        grid = unescape_json_attribute(match.group(1))
        keyfacts = grid.get("keyfacts") or {}
        path = keyfacts.get("wonCategoryPrimary") or ""
        if not is_food(path):
            continue
        price, note, packaging = _price(grid)
        canonical = grid.get("canonicalUrl") or grid.get("canonicalPath")
        products.append(
            Product(
                supermarket="lidl",
                source_id=str(grid.get("productId") or grid["itemId"]),
                name=(grid.get("fullTitle") or grid["title"]).strip(),
                url=f"{BASE}{canonical}" if canonical and canonical.startswith("/") else canonical,
                image_url=grid.get("image"),
                category=[part for part in path.split("/")[1:] if part],  # drop the "Mundos de necesidad" root
                price=to_decimal(price),
                price_note=note,
                package=packaging,
                available=None,
                scraped_at=scraped_at,
            )
        )
    return products


class LidlScraper(Scraper):
    key = "lidl"
    name = "Lidl"
    homepage = "https://www.lidl.es/"
    method = "weekly offer pages, product records embedded in each tile"
    notes = "Only the food on offer this week and next, not the permanent range: Lidl publishes no food catalogue online."

    async def products(self, ctx: ScrapeContext) -> AsyncIterator[Product]:
        for url in ctx.settings.lidl_offer_pages:
            response = await ctx.fetch_item(url)
            if response is None:
                continue
            try:
                found = parse_offer_page(response.text, ctx.now())
            except ITEM_PARSE_ERRORS as exc:
                ctx.item_failed(url, f"parse: {exc}")
                continue
            for product in found:
                yield product
