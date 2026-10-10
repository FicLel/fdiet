from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Every knob comes from an environment variable prefixed SCRAPER_ (or from .env).

    List values are written as JSON, e.g. SCRAPER_LIDL_OFFER_PAGES='["https://..."]'.
    """

    model_config = SettingsConfigDict(env_prefix="SCRAPER_", env_file=".env", extra="ignore")

    # Who we are. An honest product token lets a site's robots.txt and its operators
    # address us; put a contact URL or address in it.
    user_agent: str = "fdiet-scrapper/0.1 (+set SCRAPER_USER_AGENT to a contact URL)"
    accept_language: str = "es-ES,es;q=0.9"
    respect_robots: bool = True
    # Hosts whose robots.txt is not consulted while respect_robots is on: an operator's
    # decision per site, so turning robots off for one does not turn it off for all.
    # Throttling, retries and refusal handling still apply to them.
    robots_exempt_hosts: list[str] = []

    # Politeness. One request in flight per host, and at least this long between the end
    # of one request and the start of the next (plus a random jitter). A robots.txt
    # Crawl-delay longer than this wins.
    min_delay_seconds: float = 2.0
    jitter_seconds: float = 1.0
    timeout_seconds: float = 30.0

    # Retries: only on timeouts, connection errors, 429 and 5xx, with exponential backoff
    # that holds back the whole host, not only the failed request.
    max_retries: int = 3
    backoff_base_seconds: float = 4.0
    backoff_max_seconds: float = 120.0
    # A Retry-After longer than this aborts the run instead of waiting it out.
    max_retry_after_seconds: float = 600.0
    # This many item-level failures in a row abort a run: something is wrong, stop asking.
    max_consecutive_failures: int = 10

    # A full sync of one supermarket is refused (HTTP 429) until this long after the
    # last completed one, unless forced.
    sync_cooldown_hours: float = 12.0

    data_dir: Path = Path("data")

    # Per-supermarket settings.
    mercadona_warehouse: str = "mad1"
    # One extra request per product to /api/products/<id>/ for EAN, brand and ingredients.
    # Off: about 150 requests per sync instead of about 4,500.
    mercadona_product_details: bool = True
    lupa_sitemap: str = "https://www.lupaonline.com/media/sitemap/sitemap_santande.xml"
    lidl_offer_pages: list[str] = [
        "https://www.lidl.es/c/ofertas-semanales/a10089449",
        "https://www.lidl.es/c/ofertas-de-la-semana-c/a10089614",
        "https://www.lidl.es/c/ofertas-proxima-semana/a10088432",
    ]
    # Category ids of the food sections: frescos, alimentación, congelados, dulces y
    # desayuno, bebidas.
    eroski_food_sections: list[str] = ["2059698", "2059806", "2059919", "2060118", "2060211"]
    # ALDI sections (the first segment of /productos/<section>/...) that are not food.
    aldi_excluded_categories: list[str] = ["bazar", "cuidado-personal", "limpieza-y-hogar", "mascotas"]


@lru_cache
def get_settings() -> Settings:
    return Settings()
