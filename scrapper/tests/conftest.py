from collections.abc import Callable
from pathlib import Path

import httpx
import pytest

from app.config import Settings

Handler = Callable[[httpx.Request], httpx.Response]


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    """Real politeness logic, test-sized waits."""
    return Settings(
        _env_file=None,
        user_agent="fdiet-scrapper/test",
        min_delay_seconds=0.0,
        jitter_seconds=0.0,
        backoff_base_seconds=0.01,
        backoff_max_seconds=0.05,
        max_retries=3,
        max_retry_after_seconds=1.0,
        max_consecutive_failures=3,
        data_dir=tmp_path / "data",
    )


def routes(table: dict[str, httpx.Response | Handler], default_robots: str = "User-agent: *\nAllow: /\n") -> httpx.MockTransport:
    """A transport answering by full URL (path + query); robots.txt allows all unless routed."""
    calls: list[str] = []

    def handle(request: httpx.Request) -> httpx.Response:
        url = str(request.url)
        calls.append(url)
        answer = table.get(url)
        if answer is None and request.url.path == "/robots.txt":
            return httpx.Response(200, text=default_robots)
        if answer is None:
            return httpx.Response(404, text="not routed")
        return answer(request) if callable(answer) else answer

    transport = httpx.MockTransport(handle)
    transport.calls = calls  # type: ignore[attr-defined]
    return transport
