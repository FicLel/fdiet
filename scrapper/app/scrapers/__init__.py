from app.scrapers.aldi import AldiScraper
from app.scrapers.base import Scraper
from app.scrapers.eroski import EroskiScraper
from app.scrapers.jsonld_site import CarrefourScraper, DiaScraper
from app.scrapers.lidl import LidlScraper
from app.scrapers.lupa import LupaScraper
from app.scrapers.mercadona import MercadonaScraper

SCRAPERS: dict[str, Scraper] = {
    scraper.key: scraper
    for scraper in (
        CarrefourScraper(),
        MercadonaScraper(),
        AldiScraper(),
        DiaScraper(),
        EroskiScraper(),
        LupaScraper(),
        LidlScraper(),
    )
}
