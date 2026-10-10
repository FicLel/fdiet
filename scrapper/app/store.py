"""Snapshots on disk: one JSON Lines file per run, and `latest.jsonl` per supermarket.

A run writes `data/<key>/<UTC timestamp>.jsonl` as it goes. Only a complete run (not a
sample, not aborted) is promoted to `latest.jsonl`, by an atomic rename, so a reader never
sees a half-written catalogue and a failed sync leaves the previous one in place.
"""

import json
import os
import shutil
from datetime import datetime
from pathlib import Path
from typing import IO

from pydantic import BaseModel

from app.models import Product

LATEST = "latest.jsonl"
LATEST_META = "latest.meta.json"


class SnapshotMeta(BaseModel):
    supermarket: str
    synced_at: datetime
    products: int
    source_file: str


class SnapshotWriter:
    def __init__(self, data_dir: Path, supermarket: str, started_at: datetime) -> None:
        self.folder = data_dir / supermarket
        self.folder.mkdir(parents=True, exist_ok=True)
        self.path = self.folder / f"{started_at:%Y%m%dT%H%M%SZ}.jsonl"
        self._file: IO[str] = self.path.open("w", encoding="utf-8")
        self.count = 0

    def write(self, product: Product) -> None:
        self._file.write(product.model_dump_json() + "\n")
        self.count += 1

    def close(self) -> None:
        self._file.close()

    def promote(self, supermarket: str, synced_at: datetime) -> None:
        temporary = self.folder / f"{LATEST}.tmp"
        shutil.copyfile(self.path, temporary)
        os.replace(temporary, self.folder / LATEST)
        meta = SnapshotMeta(supermarket=supermarket, synced_at=synced_at, products=self.count, source_file=self.path.name)
        (self.folder / LATEST_META).write_text(meta.model_dump_json(indent=2), encoding="utf-8")


class SnapshotReader:
    """Reads `latest.jsonl`, cached until the file changes."""

    def __init__(self, data_dir: Path) -> None:
        self.data_dir = data_dir
        self._cache: dict[str, tuple[float, list[Product]]] = {}

    def meta(self, supermarket: str) -> SnapshotMeta | None:
        path = self.data_dir / supermarket / LATEST_META
        if not path.exists():
            return None
        return SnapshotMeta.model_validate_json(path.read_text(encoding="utf-8"))

    def products(self, supermarket: str) -> list[Product]:
        path = self.data_dir / supermarket / LATEST
        if not path.exists():
            return []
        mtime = path.stat().st_mtime
        cached = self._cache.get(supermarket)
        if cached and cached[0] == mtime:
            return cached[1]
        with path.open(encoding="utf-8") as handle:
            products = [Product.model_validate(json.loads(line)) for line in handle if line.strip()]
        self._cache[supermarket] = (mtime, products)
        return products
