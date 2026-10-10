"""Logging: one line per event, each carrying which food sync, job and supermarket it is about.

The context lives in contextvars, so a line logged deep inside an adapter or the HTTP client
still says which run it belongs to. An asyncio task copies the context it was created in;
`bind` is called at the top of each task, so two syncs running side by side never mix.

`SCRAPER_LOG_FORMAT=json` writes one JSON object per line, which docker compose ships to
OpenSearch through Fluent Bit; `text` is for a terminal. Extra fields passed as
`log.info(..., extra={"products": 12})` land in the JSON object as they are.
"""

import json
import logging
from contextvars import ContextVar
from datetime import datetime, timezone

from app.config import Settings

CONTEXT_FIELDS = ("sync_id", "phase", "job_id", "supermarket")
_context: dict[str, ContextVar[str | None]] = {name: ContextVar(name, default=None) for name in CONTEXT_FIELDS}
# Attributes every LogRecord has; anything else on a record came in through `extra`.
_STANDARD = set(logging.makeLogRecord({}).__dict__) | {"message", "asctime", "taskName"}


def bind(**values: str | None) -> None:
    """Sets context fields for the rest of the current task."""
    for name, value in values.items():
        _context[name].set(value)


def current() -> dict[str, str]:
    return {name: value for name, var in _context.items() if (value := var.get()) is not None}


def _extras(record: logging.LogRecord) -> dict[str, object]:
    return {key: value for key, value in record.__dict__.items() if key not in _STANDARD}


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entry: dict[str, object] = {
            "@timestamp": datetime.fromtimestamp(record.created, timezone.utc).isoformat(timespec="milliseconds"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "service": "scrapper",
            **current(),
            **_extras(record),
        }
        if record.exc_info:
            entry["error_stack"] = self.formatException(record.exc_info)
        return json.dumps(entry, default=str, ensure_ascii=False)


class TextFormatter(logging.Formatter):
    def __init__(self) -> None:
        super().__init__("%(asctime)s %(levelname)-7s %(name)s %(context)s%(message)s")

    def format(self, record: logging.LogRecord) -> str:
        fields = {**current(), **_extras(record)}
        record.context = "".join(f"[{key}={value}] " for key, value in fields.items())
        return super().format(record)


def configure(settings: Settings) -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter() if settings.log_format.lower() == "json" else TextFormatter())
    root = logging.getLogger()
    root.handlers[:] = [handler]
    root.setLevel(settings.log_level.upper())
    # Uvicorn's own loggers go through the same handler, so the access log is JSON too.
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        uvicorn_logger = logging.getLogger(name)
        uvicorn_logger.handlers[:] = []
        uvicorn_logger.propagate = True
    # One line per request to every supermarket is noise; the client logs what matters.
    logging.getLogger("httpx").setLevel(logging.WARNING)
