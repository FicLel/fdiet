from datetime import datetime
from decimal import Decimal
from enum import StrEnum

from pydantic import BaseModel, Field


class NutrientValue(BaseModel):
    """One line of a nutrition label, exactly as published: the label's own wording and unit.

    `value` is null when the text is not a plain number (`<0,5`, `trazas`); `raw` keeps it.
    """

    name: str
    value: float | None
    unit: str | None
    raw: str


class Product(BaseModel):
    supermarket: str
    source_id: str = Field(description="The retailer's own product id")
    name: str
    brand: str | None = None
    ean: str | None = None
    url: str | None = None
    image_url: str | None = None
    category: list[str] = Field(default_factory=list, description="Retailer's category path, top first")
    price: Decimal | None = Field(default=None, description="Shelf price in EUR")
    price_note: str | None = Field(default=None, description="e.g. 'Lidl Plus', 'offer until 2026-10-11'")
    reference_price: Decimal | None = Field(default=None, description="Price per reference unit")
    reference_unit: str | None = Field(default=None, description="kg, L, ud... as the retailer writes it")
    package: str | None = Field(default=None, description="Pack size as written: '200 g unidad', 'Garrafa 5 l'")
    available: bool | None = None
    nutrition_basis: str | None = Field(default=None, description="e.g. 'Valores medios por 100g'")
    nutrition: list[NutrientValue] = Field(default_factory=list)
    ingredients: str | None = None
    scraped_at: datetime


class JobStatus(StrEnum):
    QUEUED = "queued"
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    # The site refused us (403, bot challenge, Retry-After too long). Never retried on its own.
    BLOCKED = "blocked"
    # robots.txt disallows what the adapter needs. Nothing was fetched past robots.txt.
    DISALLOWED = "disallowed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class ItemFailure(BaseModel):
    url: str
    error: str


class Job(BaseModel):
    id: str
    supermarket: str
    status: JobStatus = JobStatus.QUEUED
    limit: int | None = Field(default=None, description="A sample run: stops after this many products")
    created_at: datetime
    started_at: datetime | None = None
    finished_at: datetime | None = None
    requests: int = 0
    products: int = 0
    failures: int = 0
    recent_failures: list[ItemFailure] = Field(default_factory=list)
    error: str | None = None
    output_file: str | None = None


class SupermarketInfo(BaseModel):
    key: str
    name: str
    homepage: str
    method: str
    notes: str
    last_sync: datetime | None = None
    last_sync_products: int | None = None


class ProductPage(BaseModel):
    supermarket: str
    synced_at: datetime | None
    total: int
    page: int
    size: int
    items: list[Product]


class FoodSyncStatus(StrEnum):
    QUEUED = "queued"
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    FAILED = "failed"
    CANCELLED = "cancelled"
    # The service stopped while the food sync ran; found so on the next start.
    INTERRUPTED = "interrupted"


class FoodSyncPhase(StrEnum):
    PREFLIGHT = "preflight"
    SCRAPE = "scrape"
    INGEST = "ingest"
    MATCH = "match"
    DONE = "done"


class FoodSyncOptions(BaseModel):
    scrape: bool = Field(description="Scrape the supermarkets first; false indexes the snapshots already on disk")
    force: bool = Field(description="Scrape even inside a supermarket's cooldown")
    supermarkets: list[str] = Field(description="The supermarkets scraped; every snapshot on disk is indexed either way")


class ScrapeEntry(BaseModel):
    supermarket: str
    job_id: str | None = None
    started_here: bool = Field(default=False, description="This food sync started the job (and cancels it if cancelled)")
    status: str
    products: int = 0
    note: str | None = None


class IngestProgress(BaseModel):
    index: str | None = None
    products: int = 0
    by_supermarket: dict[str, int] = Field(default_factory=dict)
    without_snapshot: list[str] = Field(default_factory=list)
    started_at: datetime | None = None
    finished_at: datetime | None = None


class MatchCounts(BaseModel):
    """How one half of fdiet's catalogue went. `processed + skipped` reaches `total` at the end."""

    total: int | None = Field(default=None, description="Foods the fdiet API reported; null until its first page")
    processed: int = 0
    skipped: int = Field(default=0, description="Foods with nothing to search by (no EAN and no name, or no Spanish name)")
    ean_matched: int = Field(default=0, description="At least one product with the same EAN")
    name_matched: int = Field(default=0, description="Candidates by name only - offered, never decided")
    unmatched: int = 0


class MatchProgress(BaseModel):
    index: str | None = None
    food_items: MatchCounts = Field(default_factory=MatchCounts)
    composition: MatchCounts = Field(default_factory=MatchCounts)
    started_at: datetime | None = None
    finished_at: datetime | None = None


class FoodSync(BaseModel):
    """One run of scrape -> index in OpenSearch -> match every fdiet food against the index."""

    id: str
    status: FoodSyncStatus = FoodSyncStatus.QUEUED
    phase: FoodSyncPhase = FoodSyncPhase.PREFLIGHT
    options: FoodSyncOptions
    created_at: datetime
    started_at: datetime | None = None
    finished_at: datetime | None = None
    error: str | None = None
    warnings: list[str] = Field(default_factory=list)
    scrape: dict[str, ScrapeEntry] = Field(default_factory=dict)
    ingest: IngestProgress = Field(default_factory=IngestProgress)
    match: MatchProgress = Field(default_factory=MatchProgress)
