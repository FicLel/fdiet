"""Each adapter's parser against a minimal page shaped like the live one (checked 2026-10-10).

The fixtures are hand-written to the structure the sites use, not copies of their pages.
"""

import gzip
import html
import json
from datetime import datetime, timezone
from decimal import Decimal

import pytest

from app.scrapers import aldi, eroski, lidl, lupa, mercadona
from app.scrapers.jsonld_site import parse_json_ld_page
from app.scrapers.parsing import ParseError, parse_nutrient, parse_sitemap

NOW = datetime(2026, 10, 10, tzinfo=timezone.utc)


def attr(value: object) -> str:
    return html.escape(json.dumps(value), quote=True)


def test_sitemap_index_and_gzipped_urlset():
    index = b'<?xml version="1.0"?><sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><sitemap><loc>https://a.test/p.xml</loc></sitemap></sitemapindex>'
    parsed = parse_sitemap(index)
    assert parsed.is_index and parsed.entries[0].loc == "https://a.test/p.xml"

    urlset = (
        b'<?xml version="1.0"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" '
        b'xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">'
        b"<url><loc>https://a.test/cat</loc></url>"
        b"<url><loc>https://a.test/prod-1</loc><image:image><image:loc>https://a.test/1.jpg</image:loc></image:image></url>"
        b"</urlset>"
    )
    parsed = parse_sitemap(gzip.compress(urlset))
    assert not parsed.is_index
    assert [(e.loc, e.has_image) for e in parsed.entries] == [("https://a.test/cat", False), ("https://a.test/prod-1", True)]


def test_sitemap_refuses_entity_expansion():
    bomb = b'<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a "aaaa">]><urlset><url><loc>&a;</loc></url></urlset>'
    with pytest.raises(Exception):
        parse_sitemap(bomb)


@pytest.mark.parametrize(
    ("text", "value", "unit"),
    [("154 Kcal", 154.0, "Kcal"), ("7,4 g", 7.4, "g"), ("0.78 g", 0.78, "g"), ("<0,5 g", None, None), ("trazas", None, None)],
)
def test_nutrient_values_keep_their_unit(text, value, unit):
    nutrient = parse_nutrient("grasas", text)
    assert (nutrient.value, nutrient.unit, nutrient.raw) == (value, unit, text)


def aldi_page(category_key: str = "pasta", section: str = "despensa") -> str:
    api = [
        ["PAGE_MGNL_GET", {"req": {}, "res": {}}],
        [
            "PRODUCT_DETAIL_GET",
            {
                "req": {"productIds": ["601136002"]},
                "res": {
                    "products": [
                        {
                            "objectID": "601136002",
                            "name": "Noodles ramen",
                            "brandName": "ASIA GREEN GARDEN®",
                            "salesUnit": "200 g unidad",
                            "isAvailable": True,
                            "mainCategoryID": category_key,
                            "assets": [{"type": "primary", "url": "https://img.test/1"}],
                            "currentPrice": {"priceValue": 1.35, "basePrice": [{"basePriceValue": 6.75, "basePriceScale": "kg"}]},
                        }
                    ],
                    "parentCategory": {
                        "data": {
                            "parent": {"categoryName": "Despensa", "categoryKey": "despensa", "reference": {"path": f"/productos/{section}"}},
                            "current": {"categoryName": "Pasta", "categoryKey": category_key},
                        }
                    },
                },
            },
        ],
    ]
    data = {"props": {"pageProps": {"apiData": json.dumps(api)}}}
    return f'<html><script id="__NEXT_DATA__" type="application/json">{json.dumps(data)}</script></html>'


def test_aldi_product_page():
    product = aldi.parse_product_page(aldi_page(), "https://www.aldi.es/producto/noodles-ramen-601136002.html", NOW, set())
    assert product.source_id == "601136002"
    assert product.brand == "ASIA GREEN GARDEN®"
    assert product.price == Decimal("1.35")
    assert (product.reference_price, product.reference_unit) == (Decimal("6.75"), "kg")
    assert product.category == ["Despensa", "Pasta"]
    assert product.image_url == "https://img.test/1"


def test_aldi_skips_excluded_categories_and_flags_empty_pages():
    assert aldi.parse_product_page(aldi_page("bazar"), "u", NOW, {"bazar"}) is None
    assert aldi.parse_product_page(aldi_page("herramientas", section="bazar"), "u", NOW, {"bazar"}) is None
    with pytest.raises(ParseError):
        aldi.parse_product_page("<html></html>", "u", NOW, set())


LUPA_PAGE = """
<html><head>
<script type="application/ld+json">{"@context":"https://schema.org","@graph":[{"@type":"Organization","name":"Lupa"}]}</script>
<script type="application/ld+json">{"@context":"https://schema.org","@type":"Product","name":"Queso De Autor Receta Blanda Angulo, Kilo ",
 "url":"https://www.lupaonline.com/santander/queso-002010","image":"https://img.test/2010.jpg","sku":"2010",
 "offers":{"@type":"Offer","priceCurrency":"EUR","price":"11.75","availability":"https://schema.org/InStock"}}</script>
</head><body>
<table class="nutritional-info-table"><thead><tr><th colspan="2">Valores medios por 100g</th></tr><tr><th>Nutriente</th><th>Valor</th></tr></thead>
<tbody><tr><td>energía</td><td>642 Kj</td></tr><tr><td>energía</td><td>154 Kcal</td></tr><tr><td>grasas</td><td>11 g</td></tr></tbody></table>
<div class="data item content" id="ingredients.tab">LECHE pasterizada de vaca<br> cuajo<br> sal<br>
<script>document.querySelectorAll('#ingredients.tab')</script></div>
</body></html>
"""


def test_lupa_product_page_with_label():
    product = lupa.parse_product_page(LUPA_PAGE, "https://www.lupaonline.com/santander/queso-002010", NOW)
    assert (product.source_id, product.name, product.price, product.available) == ("2010", "Queso De Autor Receta Blanda Angulo, Kilo", Decimal("11.75"), True)
    assert product.nutrition_basis == "Valores medios por 100g"
    assert [(n.name, n.value, n.unit) for n in product.nutrition] == [("energía", 642.0, "Kj"), ("energía", 154.0, "Kcal"), ("grasas", 11.0, "g")]
    assert product.ingredients == "LECHE pasterizada de vaca, cuajo, sal"


def test_eroski_leaf_categories_only_under_food_sections():
    base = "https://supermercado.eroski.es/es/supermercado/"
    locs = [
        base + "2059698-frescos/",
        base + "2059698-frescos/2059699-frutas/",
        base + "2059698-frescos/2059699-frutas/2059700-naranjas/",
        base + "2059698-frescos/2059699-frutas/2059701-manzanas/",
        base + "2059698-frescos/2059710-verduras/",
        base + "2060538-limpieza/2060539-detergentes/",
        base + "danone/",
    ]
    assert eroski.food_leaf_categories(locs, {"2059698"}) == [locs[2], locs[3], locs[4]]
    assert eroski.category_names(locs[2]) == ["frescos", "frutas", "naranjas"]


def test_eroski_listing_tiles():
    metrics = {"event": "select_item", "ecommerce": {"items": [{"price": 5.85, "item_name": "Naranja EROSKI, malla 3 kg", "item_id": "23760622", "item_brand": "EROSKI."}]}}
    page = (
        f'<h2 class="product-title"><a class="product-title-link" data-metrics="{attr(metrics)}" '
        f'href="https://supermercado.eroski.es:443/es/productdetail/23760622-naranja/">x</a></h2>'
        '<a href="https://supermercado.eroski.es/es/supermercado/x/?pageNumber=1">1</a>'
        '<a href="https://supermercado.eroski.es/es/supermercado/x/?pageNumber=24">24</a>'
    )
    [product] = eroski.parse_listing(page, ["frescos"], NOW)
    assert (product.source_id, product.brand, product.price) == ("23760622", "EROSKI", Decimal("5.85"))
    assert product.url == "https://supermercado.eroski.es/es/productdetail/23760622-naranja/"
    assert eroski.last_page(page) == 24


def test_lidl_offer_tiles_keep_food_only():
    food = {
        "productId": 11030104,
        "fullTitle": "Manzana roja dulce 1 kg bolsa",
        "canonicalUrl": "/p/manzana-roja-dulce-1-kg-bolsa/p11030104",
        "image": "https://img.test/m.png",
        "price": {"currencyCode": "EUR"},
        "lidlPlus": [{"price": {"price": 1.49, "packaging": {"text": "1kg"}}}],
        "keyfacts": {"wonCategoryPrimary": "Mundos de necesidad/Comida y cerca de la comida/Frutas y hortalizas/Fruta"},
    }
    soap = {**food, "productId": 2, "keyfacts": {"wonCategoryPrimary": "Mundos de necesidad/Comida y cerca de la comida/Productos de droguería y cuidado personal"}}
    drill = {**food, "productId": 3, "keyfacts": {"wonCategoryPrimary": "Mundos de necesidad/Bricolaje/Herramientas"}}
    page = "".join(f'<div data-grid-data="{attr(tile)}"></div>' for tile in (food, soap, drill))
    [product] = lidl.parse_offer_page(page, NOW)
    assert product.source_id == "11030104"
    assert (product.price, product.price_note, product.package) == (Decimal("1.49"), "Lidl Plus", "1kg")
    assert product.url == "https://www.lidl.es/p/manzana-roja-dulce-1-kg-bolsa/p11030104"
    assert product.category == ["Comida y cerca de la comida", "Frutas y hortalizas", "Fruta"]


def test_mercadona_category_payload():
    tree = {"results": [
        {"id": 12, "name": "Aceite, especias y salsas", "categories": [{"id": 112, "name": "Aceite, vinagre y sal", "published": True}]},
        {"id": 99, "name": "Mascotas", "categories": [{"id": 900, "name": "Perro", "published": True}]},
        {"id": 20, "name": "Bebé", "categories": [{"id": 216, "name": "Alimentación infantil"}, {"id": 217, "name": "Toallitas y pañales"}]},
    ]}
    assert mercadona.second_level_ids(tree) == [(112, "Aceite, especias y salsas"), (216, "Bebé")]
    data = {"id": 112, "name": "Aceite, vinagre y sal", "categories": [{"name": "Aceite de oliva", "products": [{
        "id": "4241", "display_name": "Aceite de oliva 0,4º Hacendado", "packaging": "Garrafa", "published": True,
        "share_url": "https://tienda.mercadona.es/product/4241/x", "thumbnail": "https://img.test/t.jpg",
        "price_instructions": {"unit_price": "17.25", "unit_size": 5.0, "size_format": "l", "reference_price": "3.450", "reference_format": "L"},
    }]}]}
    [product] = mercadona.parse_category(data, "Aceite, especias y salsas", NOW)
    assert (product.price, product.reference_price, product.reference_unit) == (Decimal("17.25"), Decimal("3.450"), "L")
    assert product.package == "Garrafa 5 l"
    assert product.category == ["Aceite, especias y salsas", "Aceite, vinagre y sal", "Aceite de oliva"]


def test_mercadona_product_details_add_ean_brand_and_ingredients():
    listed = mercadona.Product(supermarket="mercadona", source_id="10005", name="Chocolate", scraped_at=NOW)
    detail = {"id": "10005", "ean": "8480000100054", "brand": "Hacendado", "nutrition_information": {
        "ingredients": "<strong>Leche</strong> parcialmente desnatada. azúcar.",
        "allergens": "Contiene <strong>leche</strong>."}}
    product = mercadona.with_details(listed, detail)
    assert (product.ean, product.brand) == ("8480000100054", "Hacendado")
    assert product.ingredients == "Leche parcialmente desnatada. azúcar. Contiene leche."
    with pytest.raises(ParseError):
        mercadona.with_details(listed, {"code": "not_found", "en_message": "gone"})
    with pytest.raises(ParseError):
        mercadona.with_details(listed, {**detail, "id": "1"})


def test_generic_json_ld_page():
    page = '<script type="application/ld+json">[{"@type":["Product"],"name":"Leche","sku":"7","gtin13":"8410000000000","brand":{"@type":"Brand","name":"X"},"offers":[{"price":"0,95","availability":"OutOfStock"}]}]</script>'
    product = parse_json_ld_page(page, "dia", "https://www.dia.es/p/7", NOW)
    assert (product.source_id, product.ean, product.brand, product.price, product.available) == ("7", "8410000000000", "X", Decimal("0.95"), False)
