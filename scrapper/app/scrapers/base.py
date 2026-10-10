from abc import ABC, abstractmethod
from collections.abc import AsyncIterator, Callable
from datetime import datetime, timezone

import httpx

from app.config import Settings
from app.models import ItemFailure, Product
from app.net.client import NotFoundError, PoliteClient, RetriesExhaustedError, ScrapeError, UnexpectedStatusError
from app.scrapers.parsing import SitemapEntry, parse_sitemap

MAX_RECENT_FAILURES = 20


class TooManyFailuresError(ScrapeError):
    """`max_consecutive_failures` items in a row failed; the run stops."""


class ScrapeContext:
    """What an adapter gets: the client, the settings, and a place to report a bad item.

    Listing fetches (a sitemap, a category tree) go through `client.get` and any failure
    ends the run. Item fetches go through `fetch_item`, where a missing or broken item is
    recorded and skipped - until too many fail in a row, which means the site, not the
    item, is the problem.
    """

    def __init__(self, client: PoliteClient, settings: Settings, on_failure: Callable[[ItemFailure], None]):
        self.client = client
        self.settings = settings
        self._on_failure = on_failure
        self._consecutive = 0

    @staticmethod
    def now() -> datetime:
        return datetime.now(timezone.utc)

    async def fetch_item(self, url: str, params: dict[str, str] | None = None) -> httpx.Response | None:
        try:
            response = await self.client.get(url, params=params)
        except (NotFoundError, RetriesExhaustedError, UnexpectedStatusError) as exc:
            self.item_failed(url, str(exc))
            return None
        self._consecutive = 0
        return response

    def item_failed(self, url: str, error: str) -> None:
        self._on_failure(ItemFailure(url=url, error=error))
        self._consecutive += 1
        if self._consecutive >= self.settings.max_consecutive_failures:
            raise TooManyFailuresError(f"{self._consecutive} items failed in a row; last: {error}")


async def walk_sitemap(
    ctx: ScrapeContext,
    url: str,
    follow: Callable[[str], bool] = lambda _: True,
    max_depth: int = 3,
) -> list[SitemapEntry]:
    """Every <url> under `url`, descending into the index entries `follow` accepts."""
    response = await ctx.client.get(url)
    sitemap = parse_sitemap(response.content)
    if not sitemap.is_index:
        return sitemap.entries
    if max_depth == 0:
        return []
    entries: list[SitemapEntry] = []
    for child in sitemap.entries:
        if follow(child.loc):
            entries.extend(await walk_sitemap(ctx, child.loc, follow, max_depth - 1))
    return entries


class Scraper(ABC):
    key: str
    name: str
    homepage: str
    method: str
    notes: str = ""

    @abstractmethod
    def products(self, ctx: ScrapeContext) -> AsyncIterator[Product]:
        """Yield every food product the site lists. May yield the same id twice; the job dedupes."""
