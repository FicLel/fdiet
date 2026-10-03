# FD-034 Repeatable data load for new installations

Status: refining (questions answered) · Size: L (phases below become their own ids when refined) · Created: 2026-10-02
For: whoever installs fdiet (dev) — and the nutritionist, who must get the same figures on every machine

## Problem
As someone installing fdiet I want every open dataset it needs snapshotted in the repo with a manifest,
and loaded the same way on every machine by one documented step, so that an install never depends on a
live URL (upstream 404s are already happening: AESAN's legal notice, Etalab's licence page) and two
installs never disagree about a figure.

Today: `ref_*` loads at startup from `reference-data/`, but `food_items` and `bedca_foods` need a manual
`POST /api/food/sync` / `POST /api/bedca/sync`, and a reference row naming a food not yet loaded is
**skipped** — so a fresh install's result depends on the order things were run.

Source research: `reports/Open food data and permissions.md` (sections "A three-layer archive" and the
prioritised list, items 1–7, 17).

## Phases
- **A — Manifest + provenance for what is loaded now (M), can start before FD-033:** extend
  `sources.csv` (or a `datapackage.json` per folder) with `licence_id` (SPDX), `licence_url`,
  `licence_archived_url`, `source_url`, `archived_url` (Wayback), `retrieved_on`, `original_sha256`,
  `snapshot_sha256`, `rows`, `attribution`, `modifications`, `redistribute_original`, `commercial_use`.
  Cover AESAN 2022, AESAN/MEC 2010, 5 al día, USDA yields, exchange definitions and `fooddata.csv`
  (AESAN `BasedatosWeb.xlsx`). Include report items 1–4: AESAN notice repointed to
  `aesan.gob.es/aviso-legal` + archived old page; `FOODDATA-ATTRIBUTION.txt` (Kantar, 2022 labels,
  `,`→`;` change, dropped columns); seghnp.org mirror replaced or archived+hashed; CLAUDE.md `~100 k`
  → ~29.6 k products.
- **B — Deterministic, idempotent fresh load (M):** one documented step loads everything in a fixed order
  (catalogues before reference) on an empty DB; re-running changes nothing.
- **C — Check script (S):** verifies hashes, flags dead links / upstream changes, never overwrites.
- **D — Composition replacement in the manifest (S), after FD-033 phase B.**

## Acceptance criteria
- [ ] Every loaded dataset is a CSV committed in the repo, one folder per source, with a manifest row:
      source URL, archived Wayback URL (data **and** licence page), retrieval date, SHA-256 of the
      committed file (and of the original where fetched), row count, SPDX licence, attribution, modifications.
- [ ] Originals (xlsx/pdf) committed only where the licence allows redistribution; otherwise hash + archived URL.
- [ ] Nothing in install or startup fetches a URL.
- [ ] **git-lfs is a documented prerequisite** (README / install steps): install it before cloning. The
      composition originals (`.xlsx`, `.pdf`) are LFS-tracked (FD-033 decision 12, 2026-10-03); without LFS
      they are pointer files and the sync fails — the load should say so by name rather than fail obscurely.
- [ ] Fresh install: empty database + documented step(s) (`gradlew bootRun`, or one command named in the
      README) → every table filled; row counts equal the manifest's; no reference row skipped for a
      missing food.
- [ ] Two fresh installs give identical data (same codes / natural keys and same values — compare a dump
      or per-table checksum).
- [ ] Re-running the load on a loaded DB changes nothing and fails nothing; ids that diets point at stay
      put (codes / EAN / source ids are the keys).
- [ ] A snapshot file whose hash does not match the manifest is refused at load with a message naming it.
- [ ] Check script (`scripts/check-sources` or a Gradle task): offline mode verifies every
      `snapshot_sha256` (non-zero exit on mismatch → "local edit not recorded"); online mode reports per
      source: link OK / 404 / 410 / redirected to a home page, upstream hash changed, Wayback copy stale.
      It **never** writes a data file; optional Wayback capture request. Prints the attribution report.
- [ ] The UI footers' attributions come from the manifest (one place), not hand-written.
- [ ] `reference-data/README.md` documents the manifest, the load order and how to add a source;
      CLAUDE.md updated; tests green.

## Tasks
- backend: A — manifest columns + values, attribution files, README/CLAUDE.md fixes; B — single ordered
  load (catalogues → reference), hash check before load, idempotent by natural key; startup vs Flyway vs
  one endpoint is the backend's choice (note: Flyway data migrations would freeze data in schema history);
  C — check script + test of the offline mode; D — add the FD-033 dataset. Migration only if the manifest
  goes into a table (e.g. `ref_sources` columns) — restart 5000 after; `gradlew test` migrates the real DB.
- frontend: footer attribution read from the API (manifest-backed), Spanish copy; nothing else.
- tech-lead: review after B and C.

## Dependencies
- Phase D waits on FD-033 phase B (which composition DB). A–C can go now on the current open sources.
- Until FD-033 lands, BEDCA stays in the manifest marked `commercial_use = false`, original not
  redistributed (hash + archived URL only).

## Decisions (user, 2026-10-02 — see decisions.md)
1. Branded catalogue auto-loads only when its table is empty; skipped when already loaded.
2. Check script run by hand; its offline checksum mode also runs in `gradlew test`.
3. Zenodo DOI / Software Heritage deposit: later, its own story → FD-035.
- The composition snapshot (phase D) is CIQUAL 2025 + BLS 4.0 (FD-033). fdiet's Spanish-name list and
  method-only definitions are CC BY 4.0 — no `LicenseRef-*` ids in the manifest.

## Open questions
_None._

## Hand-off prompts
_Written when phase A is picked up as its own story._

## Notes
- Report item 7 (refuse a permission-required source without `permission_file`) is moot: gated sources
  are out entirely (decision 2026-10-02).
