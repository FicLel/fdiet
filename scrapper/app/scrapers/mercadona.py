"""Mercadona: tienda.mercadona.es is a single-page app over a public JSON API.

The endpoints are documented (unofficially) by https://github.com/datania/mercadona-catalog
(`api.md`): no authentication, `lang` and `wh` (warehouse, prices differ by region) as query
parameters, and a trailing slash on every path.

- `GET /api/categories/` - the tree: top-level sections, each with its second-level ids.
- `GET /api/categories/<id>/` - every product under one second-level category, with its
  price instructions. About 120 food categories.
- `GET /api/products/<id>/` - one product: EAN, brand, ingredients and allergens (HTML). No
  nutrition table: Mercadona publishes it only as a photo of the label. One request per
  product (about 4,500, roughly 3-4 h at the default pace); `SCRAPER_MERCADONA_PRODUCT_DETAILS`
  turns it off.

**robots.txt disallows `/api`.** With robots respected the run stops at its first request
with status `disallowed`; listing `tienda.mercadona.es` in `SCRAPER_ROBOTS_EXEMPT_HOSTS` is the
operator's decision to read it anyway. Throttling, retries and refusal handling still apply.
"""

import re
from collections.abc import AsyncIterator
from datetime import datetime
from typing import Any

from bs4 import BeautifulSoup

from app.models import Product
from app.scrapers.base import Scraper, ScrapeContext
from app.scrapers.parsing import ITEM_PARSE_ERRORS, ParseError, to_decimal

API = "https://tienda.mercadona.es/api"
# Top-level sections outside food (names as the API wrote them on 2026-10-10).
NOT_FOOD_TOP_LEVEL = frozenset({"Cuidado del cabello", "Cuidado facial y corporal", "Fitoterapia y parafarmacia",
                                "Limpieza y hogar", "Maquillaje", "Mascotas"})
# Second-level categories outside food inside a food section ("Bebé" keeps "Alimentación infantil").
NOT_FOOD_SECOND_LEVEL = frozenset({"Biberón y chupete", "Higiene y cuidado", "Toallitas y pañales", "Velas y decoración"})
_SPACE_BEFORE_PUNCTUATION = re.compile(r"\s+([.,;:)])")


def check_payload(data: Any) -> dict[str, Any]:
    """The API can answer 200 with an error body; that is a failed item, not an empty one."""
    if not isinstance(data, dict):
        raise ParseError("expected a JSON object")
    if "code" in data or "_error" in data:
        raise ParseError(f"API error: {data.get('en_message') or data.get('_error') or data.get('code')}")
    return data


def second_level_ids(tree: dict[str, Any]) -> list[tuple[int, str]]:
    return [
        (child["id"], top["name"])
        for top in tree.get("results") or []
        if top.get("name") not in NOT_FOOD_TOP_LEVEL
        for child in top.get("categories") or []
        if child.get("published", True) and child.get("name") not in NOT_FOOD_SECOND_LEVEL
    ]


def _package(item: dict[str, Any]) -> str | None:
    prices = item.get("price_instructions") or {}
    size = prices.get("unit_size")
    if isinstance(size, float) and size.is_integer():
        size = int(size)  # the API writes 5.0 for a 5 l bottle
    parts = (item.get("packaging"), size, prices.get("size_format"))
    return " ".join(str(part) for part in parts if part) or None


def parse_category(data: dict[str, Any], top_name: str, scraped_at: datetime) -> list[Product]:
    check_payload(data)
    products = []
    for leaf in data.get("categories") or []:
        for item in leaf.get("products") or []:
            prices = item.get("price_instructions") or {}
            products.append(
                Product(
                    supermarket="mercadona",
                    source_id=str(item["id"]),
                    name=item["display_name"].strip(),
                    url=item.get("share_url"),
                    image_url=item.get("thumbnail"),
                    category=[top_name, data.get("name", ""), leaf.get("name", "")],
                    price=to_decimal(prices.get("unit_price")),
                    reference_price=to_decimal(prices.get("reference_price")),
                    reference_unit=prices.get("reference_format"),
                    package=_package(item),
                    available=item.get("published"),
                    scraped_at=scraped_at,
                )
            )
    return products


def _text(html: str | None) -> str | None:
    if not html:
        return None
    text = " ".join(BeautifulSoup(html, "html.parser").get_text(" ").split())
    return _SPACE_BEFORE_PUNCTUATION.sub(r"\1", text) or None


def with_details(product: Product, data: dict[str, Any]) -> Product:
    """The listing product plus what only the product endpoint has. The listing's price
    stands: both answers come from the same request window."""
    check_payload(data)
    if str(data.get("id")) != product.source_id:
        raise ParseError(f"asked for product {product.source_id}, got {data.get('id')}")
    nutrition = data.get("nutrition_information") or {}
    ingredients = _text(nutrition.get("ingredients"))
    allergens = _text(nutrition.get("allergens"))
    if allergens:
        ingredients = f"{ingredients} {allergens}" if ingredients else allergens
    details = data.get("details") or {}
    return product.model_copy(update={
        "ean": data.get("ean") or None,
        "brand": data.get("brand") or details.get("brand") or None,
        "ingredients": ingredients,
    })


class MercadonaScraper(Scraper):
    key = "mercadona"
    name = "Mercadona"
    homepage = "https://tienda.mercadona.es/"
    method = "JSON API: category tree, one request per category, one per product"
    notes = "robots.txt disallows /api: 'disallowed' unless tienda.mercadona.es is in SCRAPER_ROBOTS_EXEMPT_HOSTS."

    async def products(self, ctx: ScrapeContext) -> AsyncIterator[Product]:
        params = {"lang": "es", "wh": ctx.settings.mercadona_warehouse}
        tree = check_payload((await ctx.client.get(f"{API}/categories/", params=params)).json())
        seen: set[str] = set()
        for category_id, top_name in second_level_ids(tree):
            url = f"{API}/categories/{category_id}/"
            response = await ctx.fetch_item(url, params=params)
            if response is None:
                continue
            try:
                found = parse_category(response.json(), top_name, ctx.now())
            except ITEM_PARSE_ERRORS as exc:
                ctx.item_failed(url, f"parse: {exc}")
                continue
            for product in found:
                if product.source_id in seen:  # a product can sit in two categories
                    continue
                seen.add(product.source_id)
                if ctx.settings.mercadona_product_details:
                    product = await self._detailed(ctx, product, params)
                yield product

    @staticmethod
    async def _detailed(ctx: ScrapeContext, product: Product, params: dict[str, str]) -> Product:
        """A detail that cannot be read leaves the listing product as it is, counted as a failure."""
        url = f"{API}/products/{product.source_id}/"
        response = await ctx.fetch_item(url, params=params)
        if response is None:
            return product
        try:
            return with_details(product, response.json())
        except ITEM_PARSE_ERRORS as exc:
            ctx.item_failed(url, f"parse: {exc}")
            return product
