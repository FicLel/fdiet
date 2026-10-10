"""A thin OpenSearch client over httpx: the six REST calls the food sync needs, nothing more.

httpx rather than opensearch-py keeps one HTTP stack in the service and lets the tests answer
with an `httpx.MockTransport`, as they already do for the supermarkets.

Every index is written fresh under a timestamped name and only then put behind its alias, in
one atomic alias update. A reader of the alias sees the previous complete index or the new
complete one, never a half-written one, and a failed run leaves the alias where it was.
"""

import json
import logging
from collections.abc import Iterable
from typing import Any

import httpx

log = logging.getLogger(__name__)


class SearchError(Exception):
    """OpenSearch answered with an error, or could not be reached."""


class SearchClient:
    def __init__(self, base_url: str, timeout: float, transport: httpx.AsyncBaseTransport | None = None) -> None:
        self._http = httpx.AsyncClient(base_url=base_url, timeout=timeout, transport=transport)

    async def aclose(self) -> None:
        await self._http.aclose()

    async def _call(self, method: str, path: str, *, body: Any = None, ndjson: str | None = None,
                    allow: tuple[int, ...] = ()) -> httpx.Response:
        kwargs: dict[str, Any] = {}
        if ndjson is not None:
            kwargs = {"content": ndjson.encode("utf-8"), "headers": {"Content-Type": "application/x-ndjson"}}
        elif body is not None:
            kwargs = {"json": body}
        try:
            response = await self._http.request(method, path, **kwargs)
        except httpx.TransportError as exc:
            raise SearchError(f"OpenSearch at {self._http.base_url} unreachable: {type(exc).__name__}: {exc}") from exc
        if response.is_error and response.status_code not in allow:
            raise SearchError(f"OpenSearch {method} {path}: HTTP {response.status_code}: {response.text[:500]}")
        return response

    async def ping(self) -> str:
        """The cluster's version; raises SearchError when it is not there."""
        return (await self._call("GET", "/")).json()["version"]["number"]

    async def create_index(self, index: str, body: dict[str, Any]) -> None:
        await self._call("PUT", f"/{index}", body=body)

    async def delete_index(self, index: str) -> None:
        await self._call("DELETE", f"/{index}", allow=(404,))

    async def refresh(self, index: str) -> None:
        await self._call("POST", f"/{index}/_refresh")

    async def bulk_index(self, index: str, docs: Iterable[tuple[str, dict[str, Any]]]) -> int:
        """Indexes (id, document) pairs in one _bulk request. Any rejected document fails the
        whole call: a silently thinner index is worse than a failed run."""
        lines = []
        for doc_id, doc in docs:
            lines.append(json.dumps({"index": {"_index": index, "_id": doc_id}}))
            lines.append(json.dumps(doc, ensure_ascii=False, default=str))
        if not lines:
            return 0
        answer = (await self._call("POST", "/_bulk", ndjson="\n".join(lines) + "\n")).json()
        if answer.get("errors"):
            failed = [item["index"] for item in answer["items"] if item["index"].get("error")]
            raise SearchError(f"{len(failed)} of {len(lines) // 2} documents rejected by {index}; "
                              f"first: {failed[0]['_id']}: {failed[0]['error']}")
        return len(lines) // 2

    async def msearch(self, index: str, queries: list[dict[str, Any]]) -> list[dict[str, Any]]:
        """One response per query, in order. A failed query fails the call."""
        if not queries:
            return []
        lines = []
        for query in queries:
            lines.append("{}")
            lines.append(json.dumps(query, ensure_ascii=False))
        responses = (await self._call("POST", f"/{index}/_msearch", ndjson="\n".join(lines) + "\n")).json()["responses"]
        for response in responses:
            if "error" in response:
                raise SearchError(f"_msearch on {index} failed: {json.dumps(response['error'])[:500]}")
        return responses

    async def search(self, index: str, body: dict[str, Any]) -> dict[str, Any]:
        return (await self._call("POST", f"/{index}/_search", body=body)).json()

    async def get(self, index: str, doc_id: str) -> dict[str, Any] | None:
        response = await self._call("GET", f"/{index}/_doc/{doc_id}", allow=(404,))
        return response.json().get("_source") if response.status_code == 200 else None

    async def point_alias(self, alias: str, index: str) -> list[str]:
        """Moves `alias` onto `index` in one atomic update, then deletes the indices it left.
        Returns their names."""
        response = await self._call("GET", f"/_alias/{alias}", allow=(404,))
        previous = [name for name in response.json() if name != index] if response.status_code == 200 else []
        actions: list[dict[str, Any]] = [{"remove": {"index": name, "alias": alias}} for name in previous]
        actions.append({"add": {"index": index, "alias": alias}})
        await self._call("POST", "/_aliases", body={"actions": actions})
        for name in previous:
            await self.delete_index(name)
        log.info("alias %s now points at %s", alias, index, extra={"alias": alias, "index": index, "dropped": previous})
        return previous
