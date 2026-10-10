"""Carrefour and Dia: sitemap discovery + schema.org Product JSON-LD on each product page.

Both sit behind bot protection that refuses any client that is not a real browser
(Carrefour: a Cloudflare challenge on every URL, robots.txt aside; Dia: Akamai, 403 even on
robots.txt). The client reports that as `blocked` on the first request and the run stops.
This code does not try to get past either: bypassing a site's bot protection is not
"polite scraping" any more. It is here so that, should access be granted (an allow-listed
IP, a data agreement), the adapter works without being written then.

The parsing has been checked against JSON-LD in general, not against these two sites' pages,
which could not be fetched; expect to adjust the product URL filters once they can be.
"""

from collections.abc import AsyncIterator, Callable
from datetime import datetime

from app.models import Product
from app.scrapers.base import Scraper, ScrapeContext, walk_sitemap
from app.scrapers.parsing import ITEM_PARSE_ERRORS, ParseError, first_text, json_ld_product, to_decimal


def parse_json_ld_page(page: str, supermarket: str, url: str, scraped_at: datetime) -> Product:
    data = json_ld_product(page)
    if data is None:
        raise ParseError("no schema.org Product on page")
    offers = data.get("offers") or {}
    if isinstance(offers, list):
        offers = offers[0] if offers else {}
    availability = offers.get("availability")
    return Product(
        supermarket=supermarket,
        source_id=str(data.get("sku") or data.get("productID") or url),
        name=data["name"].strip(),
        brand=first_text(data.get("brand")),
        ean=first_text(data.get("gtin13") or data.get("gtin") or data.get("gtin8")),
        url=data.get("url") or url,
        image_url=first_text(data.get("image"), key="url"),
        price=to_decimal(offers.get("price") or offers.get("lowPrice")),
        available=availability.endswith("InStock") if isinstance(availability, str) else None,
        scraped_at=scraped_at,
    )


class JsonLdSitemapScraper(Scraper):
    sitemap: str
    follow: Callable[[str], bool] = staticmethod(lambda _: True)
    is_product: Callable[[str], bool] = staticmethod(lambda _: True)

    async def products(self, ctx: ScrapeContext) -> AsyncIterator[Product]:
        entries = await walk_sitemap(ctx, self.sitemap, follow=self.follow)
        for entry in entries:
            if not self.is_product(entry.loc):
                continue
            response = await ctx.fetch_item(entry.loc)
            if response is None:
                continue
            try:
                product = parse_json_ld_page(response.text, self.key, entry.loc, ctx.now())
            except ITEM_PARSE_ERRORS as exc:
                ctx.item_failed(entry.loc, f"parse: {exc}")
                continue
            yield product


class CarrefourScraper(JsonLdSitemapScraper):
    key = "carrefour"
    name = "Carrefour"
    homepage = "https://www.carrefour.es/supermercado"
    method = "food sitemap + schema.org JSON-LD, one page per product"
    notes = "Cloudflare challenge on every URL: reported as blocked. No bypass is attempted."
    sitemap = "https://www.carrefour.es/crs/cdn-static/sitemap-food/index.xml"
    is_product = staticmethod(lambda loc: "/supermercado/" in loc and loc.rstrip("/").endswith("/p"))


class DiaScraper(JsonLdSitemapScraper):
    key = "dia"
    name = "Dia"
    homepage = "https://www.dia.es/"
    method = "sitemap + schema.org JSON-LD, one page per product"
    notes = "Akamai answers 403 even to robots.txt: reported as blocked. No bypass is attempted."
    sitemap = "https://www.dia.es/sitemap.xml"
    is_product = staticmethod(lambda loc: "/p/" in loc)
