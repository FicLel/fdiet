import asyncio
import time

import httpx
import pytest

from app.net.client import (
    BlockedError,
    NotFoundError,
    PoliteClient,
    RetriesExhaustedError,
    RobotsDisallowedError,
)
from tests.conftest import routes

URL = "https://shop.test/p/1"


def flaky(failures: int, status: int = 503, headers: dict[str, str] | None = None):
    count = {"n": 0}

    def handler(_: httpx.Request) -> httpx.Response:
        count["n"] += 1
        if count["n"] <= failures:
            return httpx.Response(status, headers=headers or {})
        return httpx.Response(200, text="ok")

    return handler


async def test_retries_transient_errors_then_succeeds(settings):
    transport = routes({URL: flaky(2)})
    async with PoliteClient(settings, transport) as client:
        assert (await client.get(URL)).text == "ok"
    assert transport.calls.count(URL) == 3


async def test_gives_up_after_max_retries(settings):
    transport = routes({URL: httpx.Response(502)})
    async with PoliteClient(settings, transport) as client:
        with pytest.raises(RetriesExhaustedError):
            await client.get(URL)
    assert transport.calls.count(URL) == settings.max_retries + 1


async def test_transport_errors_are_retried(settings):
    count = {"n": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        count["n"] += 1
        if count["n"] == 1:
            raise httpx.ConnectTimeout("slow", request=request)
        return httpx.Response(200, text="ok")

    async with PoliteClient(settings, routes({URL: handler})) as client:
        assert (await client.get(URL)).text == "ok"


async def test_retry_after_is_honoured(settings):
    transport = routes({URL: flaky(1, 429, {"Retry-After": "0.3"})})
    async with PoliteClient(settings, transport) as client:
        started = time.monotonic()
        await client.get(URL)
        assert time.monotonic() - started >= 0.3


async def test_a_retry_after_too_long_stops_instead_of_waiting(settings):
    transport = routes({URL: httpx.Response(429, headers={"Retry-After": "3600"})})
    async with PoliteClient(settings, transport) as client:
        with pytest.raises(BlockedError):
            await client.get(URL)
    assert transport.calls.count(URL) == 1


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(403, text="nope"),
        httpx.Response(503, text="<html><head><title>Just a moment...</title></head></html>"),
        httpx.Response(400, text="<html><head><title>Request Rejected</title></head></html>"),
    ],
)
async def test_a_refusal_is_never_retried(settings, response):
    transport = routes({URL: response})
    async with PoliteClient(settings, transport) as client:
        with pytest.raises(BlockedError):
            await client.get(URL)
    assert transport.calls.count(URL) == 1


async def test_not_found_is_not_retried(settings):
    transport = routes({})
    async with PoliteClient(settings, transport) as client:
        with pytest.raises(NotFoundError):
            await client.get(URL)
    assert transport.calls.count(URL) == 1


async def test_robots_is_read_once_and_obeyed(settings):
    transport = routes({URL: httpx.Response(200)}, default_robots="User-agent: *\nDisallow: /api\n")
    async with PoliteClient(settings, transport) as client:
        await client.get(URL)
        await client.get(URL)
        with pytest.raises(RobotsDisallowedError):
            await client.get("https://shop.test/api/x")
    assert transport.calls.count("https://shop.test/robots.txt") == 1
    assert "https://shop.test/api/x" not in transport.calls


async def test_robots_can_be_switched_off(settings):
    settings.respect_robots = False
    transport = routes({"https://shop.test/api/x": httpx.Response(200)}, default_robots="User-agent: *\nDisallow: /\n")
    async with PoliteClient(settings, transport) as client:
        await client.get("https://shop.test/api/x")
    assert "https://shop.test/robots.txt" not in transport.calls


async def test_robots_can_be_exempted_for_one_host_only(settings):
    settings.robots_exempt_hosts = ["shop.test"]
    transport = routes({"https://shop.test/api/x": httpx.Response(200)}, default_robots="User-agent: *\nDisallow: /api\n")
    async with PoliteClient(settings, transport) as client:
        await client.get("https://shop.test/api/x")
        with pytest.raises(RobotsDisallowedError):
            await client.get("https://other.test/api/x")
    assert "https://shop.test/robots.txt" not in transport.calls
    assert "https://other.test/robots.txt" in transport.calls


async def test_a_redirect_is_checked_against_robots(settings):
    transport = routes(
        {URL: httpx.Response(301, headers={"Location": "/api/moved"})},
        default_robots="User-agent: *\nDisallow: /api\n",
    )
    async with PoliteClient(settings, transport) as client:
        with pytest.raises(RobotsDisallowedError):
            await client.get(URL)


async def test_one_request_in_flight_per_host_and_spaced_out(settings):
    settings.min_delay_seconds = 0.05
    in_flight = {"now": 0, "max": 0}

    async def slow(_: httpx.Request) -> httpx.Response:
        in_flight["now"] += 1
        in_flight["max"] = max(in_flight["max"], in_flight["now"])
        await asyncio.sleep(0.01)
        in_flight["now"] -= 1
        return httpx.Response(200)

    class AsyncRoutes(httpx.AsyncBaseTransport):
        async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
            if request.url.path == "/robots.txt":
                return httpx.Response(200, text="")
            return await slow(request)

    async with PoliteClient(settings, AsyncRoutes()) as client:
        started = time.monotonic()
        await asyncio.gather(*(client.get(f"https://shop.test/p/{i}") for i in range(4)))
        elapsed = time.monotonic() - started
    assert in_flight["max"] == 1
    assert elapsed >= 4 * 0.05  # robots + 4 pages, each followed by the gap
