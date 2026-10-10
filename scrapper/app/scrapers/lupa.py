"""Lupa (Semark): a Magento shop, one catalogue per store region.

Discovery: the region's sitemap (`SCRAPER_LUPA_SITEMAP`, Santander by default); a product is
an <url> carrying an <image:image>, categories carry none. Data: schema.org Product JSON-LD
(name, sku, price, availability, image) plus the label tables Magento renders: the
nutrition table (`nutritional-info-table`, per 100 g as published) and the ingredients tab.
"""

from collections.abc import AsyncIterator
from datetime import datetime

from bs4 import BeautifulSoup

from app.models import NutrientValue, Product
from app.scrapers.base import Scraper, ScrapeContext, walk_sitemap
from app.scrapers.parsing import ITEM_PARSE_ERRORS, ParseError, first_text, json_ld_product, parse_nutrient, to_decimal


def _nutrition(soup: BeautifulSoup) -> tuple[str | None, list[NutrientValue]]:
    table = soup.find("table", class_="nutritional-info-table")
    if table is None:
        return None, []
    basis_cell = table.find("thead").find("th") if table.find("thead") else None
    rows = []
    for row in table.select("tbody tr"):
        cells = row.find_all("td")
        if len(cells) >= 2:
            rows.append(parse_nutrient(cells[0].get_text(" ", strip=True), cells[1].get_text(" ", strip=True)))
    return (basis_cell.get_text(" ", strip=True) if basis_cell else None), rows


def _ingredients(soup: BeautifulSoup) -> str | None:
    tab = soup.find(id="ingredients.tab")
    if tab is None:
        return None
    for script in tab.find_all("script"):
        script.decompose()
    text = ", ".join(part.strip() for part in tab.get_text("\n").split("\n") if part.strip())
    return text or None


def parse_product_page(page: str, url: str, scraped_at: datetime) -> Product:
    data = json_ld_product(page)
    if data is None:
        raise ParseError("no schema.org Product on page")
    offers = data.get("offers") or {}
    if isinstance(offers, list):
        offers = offers[0] if offers else {}
    availability = offers.get("availability")
    soup = BeautifulSoup(page, "html.parser")
    basis, nutrition = _nutrition(soup)
    return Product(
        supermarket="lupa",
        source_id=str(data.get("sku") or url.rstrip("/").rsplit("-", 1)[-1]),
        name=data["name"].strip(),
        brand=first_text(data.get("brand")),
        ean=first_text(data.get("gtin13") or data.get("gtin") or data.get("gtin8")),
        url=data.get("url") or url,
        image_url=first_text(data.get("image"), key="url"),
        price=to_decimal(offers.get("price")),
        available=availability.endswith("InStock") if isinstance(availability, str) else None,
        nutrition_basis=basis,
        nutrition=nutrition,
        ingredients=_ingredients(soup),
        scraped_at=scraped_at,
    )


class LupaScraper(Scraper):
    key = "lupa"
    name = "Lupa"
    homepage = "https://www.lupaonline.com/"
    method = "region sitemap + schema.org JSON-LD and label tables, one page per product"
    notes = (
        "Prices are the region's (SCRAPER_LUPA_SITEMAP). Its WAF rejects non-browser user agents "
        "with HTTP 400 'Request Rejected', which the client reports as blocked."
    )

    async def products(self, ctx: ScrapeContext) -> AsyncIterator[Product]:
        entries = await walk_sitemap(ctx, ctx.settings.lupa_sitemap)
        for entry in entries:
            if not entry.has_image:
                continue
            response = await ctx.fetch_item(entry.loc)
            if response is None:
                continue
            try:
                product = parse_product_page(response.text, entry.loc, ctx.now())
            except ITEM_PARSE_ERRORS as exc:
                ctx.item_failed(entry.loc, f"parse: {exc}")
                continue
            yield product
