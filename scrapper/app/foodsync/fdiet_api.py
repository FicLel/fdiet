"""Reads fdiet's own foods from the Spring backend, a page at a time.

`GET /api/food` (the branded catalogue, ~100 k rows, sorted by id) and `GET /api/composition`
(CIQUAL + BLS, ~10.6 k rows) both answer a `PageDto`: `content`, `totalElements`, `last`.
"""

import asyncio
import logging
from collections.abc import AsyncIterator
from typing import Any

import httpx

log = logging.getLogger(__name__)
FOOD_ITEMS = "/api/food"
COMPOSITION = "/api/composition"
ATTEMPTS = 3


class FdietApiError(Exception):
    """The backend answered with an error, or could not be reached."""


class FdietApi:
    def __init__(self, base_url: str, page_size: int, timeout: float = 60.0,
                 transport: httpx.AsyncBaseTransport | None = None) -> None:
        self.page_size = page_size
        self._http = httpx.AsyncClient(base_url=base_url, timeout=timeout, transport=transport)

    async def aclose(self) -> None:
        await self._http.aclose()

    async def _page(self, path: str, page: int, size: int) -> dict[str, Any]:
        for attempt in range(1, ATTEMPTS + 1):
            try:
                response = await self._http.get(path, params={"page": page, "size": size})
            except httpx.TransportError as exc:
                if attempt == ATTEMPTS:
                    raise FdietApiError(f"fdiet API at {self._http.base_url} unreachable: {type(exc).__name__}: {exc}") from exc
                log.warning("fdiet API %s page %d: %s; retrying", path, page, exc)
                await asyncio.sleep(2 ** attempt)
                continue
            if response.is_error:
                raise FdietApiError(f"fdiet API GET {path}?page={page}: HTTP {response.status_code}: {response.text[:300]}")
            return response.json()
        raise AssertionError("unreachable")

    async def ping(self) -> int:
        """How many branded foods the backend holds; raises FdietApiError when it is not there."""
        return (await self._page(FOOD_ITEMS, 0, 1))["totalElements"]

    async def pages(self, path: str) -> AsyncIterator[tuple[int, list[dict[str, Any]]]]:
        """(totalElements, content) for every page, first to last."""
        page = 0
        while True:
            answer = await self._page(path, page, self.page_size)
            yield answer["totalElements"], answer["content"]
            if answer["last"] or not answer["content"]:
                return
            page += 1
