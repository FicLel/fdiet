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
