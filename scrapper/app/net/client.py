"""The one door to the network. Every adapter fetches through `PoliteClient.get`.

What keeps a sync from looking like an attack:

- **One request in flight per host**, and a gap of `min_delay_seconds` + jitter after each
  one finishes (longer if robots.txt asks for a Crawl-delay). Concurrency is across hosts
  only, so syncing seven supermarkets at once is seven slow, separate conversations.
- **robots.txt is read first and obeyed** (`respect_robots`, on by default), except on the
  hosts the operator lists in `robots_exempt_hosts`.
- **Retries only where waiting can help** - timeouts, connection errors, 429 and 5xx - with
  exponential backoff + jitter, honouring `Retry-After`. The backoff holds back the whole
  host, so the next product page waits too instead of piling on.
- **A refusal is final.** 401/403 or a bot-challenge page raises `BlockedError` at once and
  the run stops: retrying a refusal is precisely how a client earns a ban.
"""

import asyncio
import email.utils
import random
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import datetime, timezone

import httpx

from app.config import Settings
from app.net.robots import RobotsPolicy, parse_robots

RETRY_STATUSES = frozenset({429, 500, 502, 503, 504})
REFUSAL_STATUSES = frozenset({401, 403})
# Text a WAF or bot challenge answers with in place of the page.
CHALLENGE_MARKERS = (
    "<title>Just a moment...</title>",  # Cloudflare
    "<TITLE>Access Denied</TITLE>",  # Akamai
    "<title>Request Rejected</title>",  # F5 ASM
    "captcha-delivery.com",  # DataDome
)


class ScrapeError(Exception):
    """Base for everything the client raises."""


class BlockedError(ScrapeError):
    """The site refused us. The run stops; nothing retries it."""


class RobotsDisallowedError(ScrapeError):
    """robots.txt disallows this URL for our user agent."""


class NotFoundError(ScrapeError):
    """404/410: the item is gone. Skipped, not retried."""


class RetriesExhaustedError(ScrapeError):
    """Still failing after every retry."""


class UnexpectedStatusError(ScrapeError):
    """Any other 4xx: retrying the same request will not change the answer."""


def _product_token(user_agent: str) -> str:
    return user_agent.split("/", 1)[0].split()[0]


def _retry_after_seconds(response: httpx.Response) -> float | None:
    value = response.headers.get("Retry-After")
    if not value:
        return None
    try:
        return max(0.0, float(value))
    except ValueError:
        pass
    try:
        when = email.utils.parsedate_to_datetime(value)
    except (TypeError, ValueError):
        return None
    if when.tzinfo is None:
        when = when.replace(tzinfo=timezone.utc)
    return max(0.0, (when - datetime.now(timezone.utc)).total_seconds())


def _looks_like_challenge(response: httpx.Response) -> bool:
    if response.status_code < 400:
        return False
    head = response.text[:4000]
    return any(marker in head for marker in CHALLENGE_MARKERS)


class HostThrottle:
    """Serialises one host's requests and spaces them out."""

    def __init__(self, delay: float, jitter: float) -> None:
        self.delay = delay
        self.jitter = jitter
        self._lock = asyncio.Lock()
        self._next_at = 0.0

    def hold_back(self, seconds: float) -> None:
        """No request to this host starts for at least `seconds` from now."""
        self._next_at = max(self._next_at, time.monotonic() + seconds)

    @asynccontextmanager
    async def slot(self) -> AsyncIterator[None]:
        async with self._lock:
            wait = self._next_at - time.monotonic()
            if wait > 0:
                await asyncio.sleep(wait)
            try:
                yield
            finally:
                self.hold_back(self.delay + random.uniform(0, self.jitter))


class PoliteClient:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None) -> None:
        self.settings = settings
        self.requests = 0
        self._token = _product_token(settings.user_agent)
        self._throttles: dict[str, HostThrottle] = {}
        self._robots: dict[str, RobotsPolicy] = {}
        self._robots_locks: dict[str, asyncio.Lock] = {}
        self._http = httpx.AsyncClient(
            transport=transport,
            timeout=settings.timeout_seconds,
            follow_redirects=False,
            headers={
                "User-Agent": settings.user_agent,
                "Accept-Language": settings.accept_language,
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.5",
            },
        )

    async def __aenter__(self) -> "PoliteClient":
        return self

    async def __aexit__(self, *_: object) -> None:
        await self.aclose()

    async def aclose(self) -> None:
        await self._http.aclose()

    async def get(self, url: str, params: dict[str, str] | None = None) -> httpx.Response:
        """GET with robots, throttling and retries. Follows up to five redirects, each one
        checked against robots and throttled like any other request."""
        target = httpx.URL(url, params=params)
        for _ in range(6):
            response = await self._get_once(target)
            if not response.is_redirect:
                return response
            target = target.join(response.headers["Location"])
        raise UnexpectedStatusError(f"too many redirects from {url}")

    async def _get_once(self, url: httpx.URL) -> httpx.Response:
        if self.settings.respect_robots and url.host not in self.settings.robots_exempt_hosts:
            policy = await self._policy(url)
            path = url.raw_path.decode("ascii", "replace")
            if not policy.allowed(path):
                raise RobotsDisallowedError(f"robots.txt of {url.host} disallows {path}")
        return await self._fetch(url)

    async def _fetch(self, url: httpx.URL) -> httpx.Response:
        throttle = self._throttle(url.host)
        attempt = 0
        while True:
            problem: str
            wait: float | None = None
            async with throttle.slot():
                self.requests += 1
                try:
                    response = await self._http.get(url)
                except httpx.TransportError as exc:
                    response = None
                    problem = f"{type(exc).__name__}: {exc}"

            if response is not None:
                status = response.status_code
                if status < 400:
                    return response
                if status in REFUSAL_STATUSES or _looks_like_challenge(response):
                    raise BlockedError(f"{url.host} refused {url} with HTTP {status}")
                if status in (404, 410):
                    raise NotFoundError(f"HTTP {status} for {url}")
                if status not in RETRY_STATUSES:
                    raise UnexpectedStatusError(f"HTTP {status} for {url}")
                problem = f"HTTP {status}"
                wait = _retry_after_seconds(response)
                if wait is not None and wait > self.settings.max_retry_after_seconds:
                    raise BlockedError(f"{url.host} asked us to wait {wait:.0f} s; stopping")

            attempt += 1
            if attempt > self.settings.max_retries:
                raise RetriesExhaustedError(f"{problem} for {url} after {attempt} attempts")
            if wait is None:
                backoff = self.settings.backoff_base_seconds * 2 ** (attempt - 1)
                wait = min(self.settings.backoff_max_seconds, backoff)
                wait += random.uniform(0, self.settings.jitter_seconds)
            throttle.hold_back(wait)

    def _throttle(self, host: str) -> HostThrottle:
        throttle = self._throttles.get(host)
        if throttle is None:
            throttle = HostThrottle(self.settings.min_delay_seconds, self.settings.jitter_seconds)
            self._throttles[host] = throttle
        return throttle

    async def _policy(self, url: httpx.URL) -> RobotsPolicy:
        origin = f"{url.scheme}://{url.netloc.decode('ascii')}"
        lock = self._robots_locks.setdefault(origin, asyncio.Lock())
        async with lock:
            policy = self._robots.get(origin)
            if policy is None:
                policy = await self._load_robots(origin)
                self._robots[origin] = policy
                if policy.crawl_delay:
                    throttle = self._throttle(url.host)
                    throttle.delay = max(throttle.delay, policy.crawl_delay)
            return policy

    async def _load_robots(self, origin: str) -> RobotsPolicy:
        # RFC 9309: unreachable (5xx, network) = disallow everything; 4xx = no rules.
        # A refusal (403, bot challenge) propagates as BlockedError: the site is not
        # talking to us at all, and saying so is truer than "robots disallows".
        try:
            response = await self._fetch(httpx.URL(f"{origin}/robots.txt"))
        except (NotFoundError, UnexpectedStatusError):
            return RobotsPolicy.allow_all()
        except RetriesExhaustedError:
            return RobotsPolicy.disallow_all()
        return parse_robots(response.text, self._token)
