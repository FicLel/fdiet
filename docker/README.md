# Running fdiet with docker compose

`compose.yaml` (repository root) starts the whole project:

| service | what | host URL |
| --- | --- | --- |
| `mysql` | MySQL 8.4, volume `mysql-data` | `127.0.0.1:3307` |
| `backend` | Spring Boot, runs the Flyway migrations and loads CIQUAL + BLS on first start | http://localhost:5000/swagger-ui.html |
| `ui` | the Vue build behind nginx, `/api` proxied to the backend | http://localhost:8080 |
| `scrapper` | FastAPI: supermarket scrapes and the food sync | http://localhost:8000/docs |
| `opensearch` | OpenSearch 2.19, volume `opensearch-data` | http://localhost:9200 |
| `opensearch-dashboards` | browse products, matches and logs | http://localhost:5601 |
| `fluent-bit` | ships every app container's output to OpenSearch (`fdiet-logs-*`) | - |

Every port is bound to `127.0.0.1`. **There is no authentication anywhere in this stack**
(fdiet has none by design, and OpenSearch runs with its security plugin off), so do not
publish these ports beyond your machine.

Scraped data stays local: the snapshots in `scrapper/data/` (git-ignored, bind-mounted) and
the copy in the `opensearch-data` volume. Neither carries an open licence (see
`scrapper/README.md`, "Licensing").

## First start

The composition snapshots are Git LFS files and are copied into the backend image:

```sh
git lfs install
git lfs pull
docker compose up -d --build
docker compose ps                       # wait for backend "healthy" (first start: a few minutes)
```

Optional settings (ports, MySQL credentials, OpenSearch heap) are the `FDIET_*` entries of
`.env.example`, copied to `.env`. Scrapper settings (user agent, `SCRAPER_ROBOTS_EXEMPT_HOSTS`
for Mercadona) go in `scrapper/.env`, which compose reads if it exists.

## Ingesting

PowerShell: use `curl.exe` (plain `curl` is an alias of `Invoke-WebRequest` there).

1. The branded catalogue (fooddata.csv, ~100 k rows) into MySQL - once:

   ```sh
   curl.exe -X POST http://localhost:5000/api/food/sync
   ```

   CIQUAL + BLS load on their own at the backend's first start; to reload them:
   `curl.exe -X POST http://localhost:5000/api/composition/sync`.

2. The food sync - fire and forget, answers at once with the run and its `id`:

   ```sh
   # everything: scrape every supermarket (hours: ALDI ~2 h, Mercadona ~3-4 h), index, match
   curl.exe -X POST "http://localhost:8000/food-sync"

   # only some supermarkets
   curl.exe -X POST "http://localhost:8000/food-sync?supermarket=aldi&supermarket=eroski&supermarket=lidl"

   # no scraping: index the snapshots already in scrapper/data and match (minutes)
   curl.exe -X POST "http://localhost:8000/food-sync?scrape=false"

   # scrape even inside the 12 h cooldown
   curl.exe -X POST "http://localhost:8000/food-sync?force=true"
   ```

3. Its status:

   ```sh
   curl.exe http://localhost:8000/food-sync/latest
   curl.exe http://localhost:8000/food-sync/<id>
   curl.exe http://localhost:8000/food-sync                # every run, newest first
   curl.exe http://localhost:8000/jobs                     # the scrape jobs, live counts
   curl.exe -X POST http://localhost:8000/food-sync/<id>/cancel
   ```

   PowerShell, polling every 30 s:

   ```powershell
   while ($true) { $s = Invoke-RestMethod http://localhost:8000/food-sync/latest; "{0} {1} {2} products, {3}/{4} branded, {5}/{6} composition" -f $s.status, $s.phase, $s.ingest.products, $s.match.food_items.processed, $s.match.food_items.total, $s.match.composition.processed, $s.match.composition.total; if ($s.finished_at) { break }; Start-Sleep 30 }
   ```

4. The result:

   ```sh
   curl.exe "http://localhost:8000/search/products?q=leche%20entera"
   curl.exe "http://localhost:8000/search/products?ean=8480000123456"
   curl.exe http://localhost:8000/food-matches/food_item/1
   curl.exe http://localhost:8000/food-matches/composition/42
   curl.exe "http://localhost:9200/_cat/indices/fdiet-*?v"
   curl.exe "http://localhost:9200/_cat/aliases/fdiet-*?v"
   ```

## Logs

```sh
docker compose logs -f scrapper                  # still works beside the Fluent Bit driver
docker compose logs -f backend
curl.exe "http://localhost:9200/fdiet-logs-*/_search?q=sync_id:<id>&sort=@timestamp:desc&size=50&pretty"
curl.exe "http://localhost:9200/fdiet-logs-*/_search?q=level:WARNING&sort=@timestamp:desc&pretty"
```

In OpenSearch Dashboards, create the index patterns once (or in the UI: Dashboards Management
-> Index patterns):

```sh
curl.exe -X POST http://localhost:5601/api/saved_objects/index-pattern/fdiet-logs -H "osd-xsrf: true" -H "Content-Type: application/json" -d "{\"attributes\":{\"title\":\"fdiet-logs-*\",\"timeFieldName\":\"ingested_at\"}}"
curl.exe -X POST http://localhost:5601/api/saved_objects/index-pattern/fdiet-products -H "osd-xsrf: true" -H "Content-Type: application/json" -d "{\"attributes\":{\"title\":\"fdiet-products\",\"timeFieldName\":\"synced_at\"}}"
curl.exe -X POST http://localhost:5601/api/saved_objects/index-pattern/fdiet-food-matches -H "osd-xsrf: true" -H "Content-Type: application/json" -d "{\"attributes\":{\"title\":\"fdiet-food-matches\",\"timeFieldName\":\"matched_at\"}}"
```

then Discover -> `fdiet-logs-*`, filter `sync_id`, `supermarket`, `phase` or `level`.
Scrapper lines are JSON with those fields; backend lines are Spring's ECS JSON (dots become
underscores: `log_level`, `log_logger`); MySQL and nginx lines stay plain text in `log`.
`ingested_at` is when Fluent Bit received a line, `@timestamp` when the app wrote it.

## Stopping and resetting

```sh
docker compose stop                     # keep everything
docker compose down                     # remove containers, keep the volumes
docker compose down -v                  # also DELETE the MySQL database and the OpenSearch indices
```

`scrapper/data/` is on the host and survives `down -v`.

## Troubleshooting

- **A container will not start, "fluentd: connection refused"**: should not happen
  (`fluentd-async`); if it does, `docker compose up -d fluent-bit` first.
- **OpenSearch exits with "max virtual memory areas vm.max_map_count [65530] is too low"**
  (Linux hosts): `sudo sysctl -w vm.max_map_count=262144`.
- **The composition sync fails in the backend log**: the `.xlsx` files are LFS pointers;
  `git lfs pull`, then `docker compose build backend`.
- **A food sync is `failed` in `preflight`**: OpenSearch or the backend did not answer; its
  `error` says which. Nothing was scraped.
