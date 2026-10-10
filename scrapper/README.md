# fdiet scrapper

A FastAPI service that syncs the food catalogue of seven Spanish supermarkets into JSON
Lines snapshots, for the fdiet backend to read. It is a **sync mechanism, not a crawler you
leave running**: a sync is started on request, runs in the background, and is refused
again for that supermarket until a cooldown has passed.

## Run

Python 3.12+.

```sh
cd scrapper
python -m venv .venv
.venv/Scripts/python -m pip install -e ".[dev]"     # Unix: .venv/bin/python
cp .env.example .env                                  # optional; set SCRAPER_USER_AGENT
.venv/Scripts/python -m uvicorn app.main:app --port 8000
.venv/Scripts/python -m pytest
```

Swagger UI at `http://localhost:8000/docs`. The Java backend uses port 5000, so 8000 does not
clash with it.

## Endpoints

| | |
| --- | --- |
| `GET /supermarkets` | each adapter, how it reads the site, its known limits, the last complete sync |
| `POST /sync/{key}?limit=&force=` | start a sync (202 + job). 409 while one runs, 429 inside the cooldown. `limit` = sample run of N products, never promoted to latest and not subject to the cooldown |
| `POST /sync?force=` | start every supermarket that is neither running nor cooling down |
| `GET /jobs`, `GET /jobs/{id}` | status, products, requests made, item failures (the last 20) |
| `POST /jobs/{id}/cancel` | stop a run; the previous snapshot stays |
| `GET /products/{key}?q=&page=&size=` | the latest complete snapshot, filtered by name |

Job status: `succeeded`, `blocked` (the site refused us), `disallowed` (robots.txt forbids
what the adapter needs), `failed`, `cancelled`.

## Not getting banned

Everything goes through `app/net/client.py`:

- **One request in flight per host**, then a pause of `SCRAPER_MIN_DELAY_SECONDS` (2 s) plus
  up to `SCRAPER_JITTER_SECONDS` (1 s) before the next one. A robots.txt `Crawl-delay` longer
  than that wins. Syncs of different supermarkets run in parallel, since they hit different hosts.
- **robots.txt first**, parsed per RFC 9309 (longest match, `*` and `$` wildcards; the
  standard library's parser handles neither). Redirect targets are checked too.
- **Retries only where waiting helps**: timeouts, connection errors, 429, 500/502/503/504.
  Exponential backoff (4 s, 8 s, 16 s, at most 120 s) with jitter, and `Retry-After` honoured.
  The backoff holds back the whole host, not only the request that failed.
- **A refusal stops the run**: 401/403, a bot-challenge page (Cloudflare, Akamai, F5,
  DataDome), or a `Retry-After` longer than 10 minutes. Retrying a refusal is how a
  client gets banned.
- **Ten item failures in a row stop the run**: by then the site is the problem, not the item.
- **A cooldown between full syncs** (12 h), also after a refused run, so a timer or a
  repeated click cannot turn into back-to-back crawls.
- A run that ends with no products, or fails, never replaces the previous snapshot.

## The supermarkets (as checked on 2026-10-10)

| key | how | what you get | status today |
| --- | --- | --- | --- |
| `aldi` | products sitemap -> each product page's embedded Next.js data | name, brand, pack size, price, price/kg, category | works; ~2,800 pages, about 2 h |
| `eroski` | category sitemap -> product tiles of each food leaf category, `?pageNumber=` | name, brand, price, category | works; listing pages, ~20 products a request |
| `lidl` | weekly offer pages, product record embedded in each tile | name, price (or Lidl Plus price), pack size, category | works; **offers only**, Lidl sells no food online |
| `lupa` | region sitemap -> schema.org JSON-LD + label tables | name, sku, price, availability, **nutrition per 100 g**, **ingredients** | **blocked**: its WAF answers HTTP 400 to non-browser user agents |
| `mercadona` | public JSON API (endpoints from [datania/mercadona-catalog](https://github.com/datania/mercadona-catalog)): category tree, one request per food category (~120), one per product (~4,500) | name, **EAN**, brand, price, price per unit, pack size, category, **ingredients + allergens**; no nutrition table (published only as a label photo) | works **only with `tienda.mercadona.es` in `SCRAPER_ROBOTS_EXEMPT_HOSTS`**: robots.txt disallows `/api`. About 3-4 h; `SCRAPER_MERCADONA_PRODUCT_DETAILS=false` skips the per-product requests (~2 min, no EAN or ingredients) |
| `carrefour` | food sitemap -> schema.org JSON-LD | (untested) | **blocked**: Cloudflare challenge on every URL |
| `dia` | sitemap -> schema.org JSON-LD | (untested) | **blocked**: Akamai, 403 even on robots.txt |

The code does not try to get past bot protection (no browser impersonation, no TLS
fingerprint spoofing, no challenge solving). That stops being polite scraping. A blocked
adapter is left in place so it works if access is granted (an allow-listed IP, a data
agreement).

Two of the three blocked results are settings, and **whoever runs the sync decides them**:

- `SCRAPER_USER_AGENT` set to a browser string gets Lupa's pages (checked: the parser reads
  name, price, the full nutrition table and the ingredients). It also hides who is asking.
- `SCRAPER_ROBOTS_EXEMPT_HOSTS=["tienda.mercadona.es"]` lets the Mercadona adapter run while
  robots.txt stays obeyed everywhere else (`SCRAPER_RESPECT_ROBOTS=false` would switch it off
  for every site). Throttling, retries and refusal handling still apply to an exempt host.

## Output

`data/<key>/<UTC timestamp>.jsonl` per run, written as it goes; `latest.jsonl` +
`latest.meta.json` replaced by an atomic rename once a full run succeeds. One `Product` per
line (`app/models.py`). Nutrition values are kept **as published, each with its own unit and
original text**, as fdiet stores composition data: `{"name": "energía", "value": 154.0,
"unit": "Kcal", "raw": "154 Kcal"}`. A value that is not a plain number (`<0,5`, `trazas`) is
`null` with `raw` kept.

## The food sync (scrape -> OpenSearch -> match fdiet's foods)

Searching seven supermarkets live for each of fdiet's ~110 k foods would take days and be
anything but polite. The food sync does it the other way round, once, in the background:

1. **preflight** - OpenSearch and the fdiet backend must answer, before hours of scraping.
2. **scrape** - one scrape job per supermarket asked for, through the same `JobManager`, so
   cooldowns, robots, throttling and refusal handling all apply. A supermarket inside its
   cooldown or refused is not scraped again; its last complete snapshot is used and the run's
   `warnings` say so.
3. **ingest** - every supermarket's `latest.jsonl` into a new index `fdiet-products-<ts>`,
   then the alias `fdiet-products` moves onto it atomically and the old index is dropped.
4. **match** - every food of `GET /api/food` (branded, by **EAN** and by name) and of
   `GET /api/composition` (CIQUAL / BLS, by **Spanish name and aliases** only) searched in
   that index, collapsed per supermarket (best 3 of each), into `fdiet-food-matches-<ts>` behind
   the alias `fdiet-food-matches`. A match is `ean` (same barcode, compared as GTIN-14) or
   `name` (a ranked candidate; like every name similarity in fdiet, offered, never decided).

A failed or cancelled run deletes its half-built index and leaves the aliases on the last
complete ones. One food sync at a time. Its status is written to
`data/food-syncs/<id>.json` at every step; a run found `running` after a restart is
`interrupted` (nothing resumes it; start a new one with `scrape=false` to reuse the snapshots).

| | |
| --- | --- |
| `POST /food-sync?scrape=&force=&supermarket=` | fire and forget (202 + the run). `scrape=false` indexes and matches the snapshots on disk; `supermarket` is repeatable (default all); 409 while one runs |
| `GET /food-sync`, `GET /food-sync/latest`, `GET /food-sync/{id}` | status: `phase` (`preflight`, `scrape`, `ingest`, `match`, `done`), each scrape job, products indexed per supermarket, foods matched (`total`, `processed`, `skipped`, `ean_matched`, `name_matched`, `unmatched`) per half of the catalogue |
| `POST /food-sync/{id}/cancel` | stops it and the scrape jobs it started |
| `GET /search/products?q=&ean=&supermarket=` | the indexed products, from OpenSearch |
| `GET /food-matches/{food_item\|composition}/{id}` | where one fdiet food is sold, per the last food sync |

Modules: `app/foodsync/` - `manager.py` (the run), `phases.py` (ingest, match),
`matching.py` (pure: query and answer), `indices.py` (mappings, Spanish analyser),
`opensearch.py` (httpx REST client), `fdiet_api.py` (the backend's pages).

## Logs

`app/logs.py`. Every line carries the `sync_id`, `phase`, `job_id` and `supermarket` it
belongs to (contextvars, so a line from deep inside the HTTP client still says which run it
is). A running scrape logs its progress every `SCRAPER_LOG_PROGRESS_EVERY` products; retries,
refusals and item failures are warnings. `SCRAPER_LOG_FORMAT=json` (set in docker compose)
writes one JSON object per line, which Fluent Bit ships to OpenSearch (`fdiet-logs-*`).

## Licensing

Unlike every dataset in `reference-data/`, **scraped catalogue data carries no open licence**:
names, prices, photos and label text belong to the retailers and manufacturers, and each
site's terms of use apply. `data/` is git-ignored and must stay out of the repository. Read
each retailer's terms (and get advice) before using the data beyond private, internal use.

## Layout

- `app/net/client.py` - `PoliteClient`: robots, throttle, retries, refusal detection.
- `app/net/robots.py` - RFC 9309 robots.txt parser.
- `app/scrapers/` - one adapter per site (`Scraper.products(ctx)` yields `Product`s), each with
  a pure `parse_*` function the tests run on a string; `base.py` has `ScrapeContext` and the
  sitemap walker, `parsing.py` the shared helpers.
- `app/jobs.py` - background runs, one per supermarket, cooldowns.
- `app/store.py` - snapshot writer / reader.
- `app/foodsync/` - the food sync (above).
- `app/logs.py` - JSON / text log lines with their run context.
- `app/main.py` - the FastAPI routes.
