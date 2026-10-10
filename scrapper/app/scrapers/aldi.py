"""ALDI Spain: no online shop, but every product of the range has a public page.

Discovery: the products sitemap (~2,800 URLs). Data: each page is a Next.js page whose
`__NEXT_DATA__` carries the product API answer (`PRODUCT_DETAIL_GET`): name, brand, sales
unit, current price and price per kg/L, category. No EAN, nutrition or ingredients.
"""

import json
import re
from collections.abc import AsyncIterator
from datetime import datetime
from typing import Any

from app.models import Product
from app.scrapers.base import Scraper, ScrapeContext, walk_sitemap
from app.scrapers.parsing import ITEM_PARSE_ERRORS, ParseError, to_decimal

SITEMAP = "https://www.aldi.es/.aldi-nord-sitemap.xml"
_NEXT_DATA = re.compile(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', re.S)


def parse_product_page(page: str, url: str, scraped_at: datetime, excluded: set[str]) -> Product | None:
    """The product on an ALDI product page; None when its category is not food."""
    match = _NEXT_DATA.search(page)
    if not match:
        raise ParseError("no __NEXT_DATA__ on page")
    page_props = json.loads(match.group(1))["props"]["pageProps"]
    api_data = page_props.get("apiData")
    calls = json.loads(api_data) if isinstance(api_data, str) else api_data or []
    detail = next((call[1] for call in calls if call and call[0] == "PRODUCT_DETAIL_GET"), None)
    products = ((detail or {}).get("res") or {}).get("products") or []
    if not products:
        raise ParseError("no PRODUCT_DETAIL_GET product in __NEXT_DATA__")
    item: dict[str, Any] = products[0]

    parent_category = (((detail.get("res") or {}).get("parentCategory") or {}).get("data")) or {}
    parent = parent_category.get("parent") or {}
    current = parent_category.get("current") or {}
    # The section is the first segment of the category's path: /productos/<section>/...
    paths = [((c.get("reference") or {}).get("path") or "") for c in (parent, current)]
    sections = {path.strip("/").split("/")[1] for path in paths if path.count("/") >= 2}
    keys = sections | {parent.get("categoryKey"), current.get("categoryKey"), item.get("mainCategoryID")}
    if keys & excluded:
        return None

    price = item.get("currentPrice") or {}
    base_prices = price.get("basePrice") or [{}]
    assets = item.get("assets") or []
    image = next((a.get("url") for a in assets if a.get("type") == "primary"), None)

    return Product(
        supermarket="aldi",
        source_id=str(item["objectID"]),
        name=item["name"],
        brand=item.get("brandName"),
        url=url,
        image_url=image,
        category=[name for name in (parent.get("categoryName"), current.get("categoryName")) if name],
        price=to_decimal(price.get("priceValue")),
        reference_price=to_decimal(base_prices[0].get("basePriceValue")),
        reference_unit=base_prices[0].get("basePriceScale"),
        package=item.get("salesUnit"),
        available=item.get("isAvailable"),
        scraped_at=scraped_at,
    )


class AldiScraper(Scraper):
    key = "aldi"
    name = "ALDI"
    homepage = "https://www.aldi.es/"
    method = "products sitemap + embedded Next.js product data, one page per product"
    notes = "No online shop: prices are the national shelf price (Canarias/Baleares may differ). No EAN or nutrition."

    async def products(self, ctx: ScrapeContext) -> AsyncIterator[Product]:
        entries = await walk_sitemap(ctx, SITEMAP, follow=lambda loc: "products" in loc)
        excluded = set(ctx.settings.aldi_excluded_categories)
        for entry in entries:
            if "/producto/" not in entry.loc:
                continue
            response = await ctx.fetch_item(entry.loc)
            if response is None:
                continue
            try:
                product = parse_product_page(response.text, entry.loc, ctx.now(), excluded)
            except ITEM_PARSE_ERRORS as exc:
                ctx.item_failed(entry.loc, f"parse: {exc}")
                continue
            if product is not None:
                yield product
