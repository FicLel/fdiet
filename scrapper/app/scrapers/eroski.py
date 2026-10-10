"""Eroski: the online supermarket at supermercado.eroski.es.

Discovery: the sitemap lists every category; only the leaves of the food sections
(`SCRAPER_EROSKI_FOOD_SECTIONS`) are read, so a product is fetched once per leaf rather
than once per level of the tree. Data: each product tile's analytics payload
(`data-metrics`, GA4 `select_item`): id, name, brand, price. Listing pages only - about
20 products per request - with `?pageNumber=N` (the bare URL is page 0).
"""

import re
from collections.abc import AsyncIterator
from datetime import datetime

from bs4 import BeautifulSoup

from app.models import Product
from app.scrapers.base import Scraper, ScrapeContext, walk_sitemap
from app.scrapers.parsing import ITEM_PARSE_ERRORS, to_decimal, unescape_json_attribute

SITEMAP = "https://supermercado.eroski.es/sitemap.xml"
_CATEGORY = re.compile(r"/es/supermercado/(\d+-[^/]+(?:/\d+-[^/]+)*)/$")
_PAGE_NUMBER = re.compile(r"[?&]pageNumber=(\d+)")


def food_leaf_categories(locs: list[str], sections: set[str]) -> list[str]:
    """Category URLs under a food section that no other category URL extends. O(n log n)."""
    categories = sorted(loc for loc in locs if (m := _CATEGORY.search(loc)) and m.group(1).split("-", 1)[0] in sections)
    return [loc for i, loc in enumerate(categories) if i + 1 == len(categories) or not categories[i + 1].startswith(loc)]


def category_names(url: str) -> list[str]:
    match = _CATEGORY.search(url)
    if not match:
        return []
    return [segment.split("-", 1)[1].replace("-", " ") for segment in match.group(1).split("/")]


def last_page(page: str) -> int:
    return max((int(n) for n in _PAGE_NUMBER.findall(page)), default=0)


def parse_listing(page: str, category: list[str], scraped_at: datetime) -> list[Product]:
    soup = BeautifulSoup(page, "html.parser")
    products = []
    for link in soup.select("a.product-title-link[data-metrics]"):
        metrics = unescape_json_attribute(link["data-metrics"])
        for item in (metrics.get("ecommerce") or {}).get("items") or []:
            products.append(
                Product(
                    supermarket="eroski",
                    source_id=str(item["item_id"]),
                    name=item["item_name"].strip(),
                    brand=(item.get("item_brand") or "").strip(" .") or None,
                    url=link.get("href", "").replace(":443/", "/") or None,
                    category=category,
                    price=to_decimal(item.get("price")),
                    scraped_at=scraped_at,
                )
            )
    return products


class EroskiScraper(Scraper):
    key = "eroski"
    name = "Eroski"
    homepage = "https://supermercado.eroski.es/"
    method = "category sitemap + product tiles of each food leaf category, paginated"
    notes = "Listing data only: price and brand, no EAN or nutrition. Prices are the default delivery area's."

    async def products(self, ctx: ScrapeContext) -> AsyncIterator[Product]:
        entries = await walk_sitemap(ctx, SITEMAP)
        sections = set(ctx.settings.eroski_food_sections)
        for category_url in food_leaf_categories([e.loc for e in entries], sections):
            category = category_names(category_url)
            seen: set[str] = set()
            page_number, pages = 0, 0
            while page_number <= pages:
                params = {"pageNumber": str(page_number)} if page_number else None
                response = await ctx.fetch_item(category_url, params=params)
                if response is None:
                    break
                try:
                    found = parse_listing(response.text, category, ctx.now())
                except ITEM_PARSE_ERRORS as exc:
                    ctx.item_failed(str(response.url), f"parse: {exc}")
                    break
                if page_number == 0:
                    pages = last_page(response.text)
                fresh = [p for p in found if p.source_id not in seen]
                if not fresh:
                    break
                seen.update(p.source_id for p in fresh)
                for product in fresh:
                    yield product
                page_number += 1
