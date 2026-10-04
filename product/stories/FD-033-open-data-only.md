# FD-033 Open data only — replace and remove BEDCA and every non-open source

Status: in progress (A done; B done, committed 2e999db; C done, committed 901f635; D done, committed 3ced939 2026-10-04; E done 2026-10-04, uncommitted) · Size: L (epic — phases below become their own ids when refined) · Created: 2026-10-02
For: nutritionist (and anyone who installs or reuses fdiet)

## Problem
As the maintainer of an open-source app I want every dataset in the repo and the database to be under
an open licence, so that anyone may install, reuse, modify or run fdiet commercially without asking
anybody — today BEDCA (non-commercial, no modification) caps the whole app.

Open = CC0 / public domain, CC BY, CC BY-SA, ODbL, Etalab 2.0, OGL v3, NLOD, or the AESAN
public-sector reuse notice (Ley 37/2007). Anything NC, ND, "personal use", "all rights reserved" or
"ask first" fails.

Source research: `reports/Open food data and permissions.md` (+ `research_notes/Open food data and permissions/`).

## Audit (2026-10-02, from the report and `reference-data/`)
| Source | Where | Licence | Verdict |
| --- | --- | --- | --- |
| BEDCA v1.0 | `bedca_foods.csv`, `BEDCA-ATTRIBUTION.txt`, `bedca_foods`, every `bedca_food_id` | non-commercial, no modification | **remove** (after replacement) |
| AESAN 2022 branded (`fooddata.csv`) | `food_items` | AESAN reuse notice | keep |
| AESAN-2022-007, AESAN/MEC 2010 | `reference-data/aesan-*` | AESAN reuse notice (notice URL now 404 — FD-034) | keep |
| 5 al día 2019 | `reference-data/5aldia-2019` | CC BY-SA 4.0 | keep; its `bedca_food_id` column must be re-keyed |
| USDA yields 2014 | `reference-data/usda-yields-2014` | US public domain | keep |
| Fundación Diabetes HC ration, Russolillo 2011 exchanges | `exchange_systems.csv` | definition (10 g) only, no table copied | keep, documented **method-only** |
| SENC, DIAL, FINUT, Russolillo lists, Murillo / SJD tables, TABULA, Moreiras, ASPCAT (NC-ND), AEP 2018, FAO/INFOODS (NC-SA), NEVO, Frida, INSA | not loaded (verify) | gated / NC / ND / unclear | must stay absent |

## What BEDCA carries today (why this is L)
BEDCA is the composition half every match, total, alternative and ration runs on: ~70 backend and
~24 UI files name it. Keyed on its ids or its Spanish names: `recipe_ingredients.bedca_food_id`,
`extra_foods.bedca_food_id`, `ref_rations` / `ref_food_measures.bedca_food_id` (published rows, diet
criteria, the nutritionist's global criteria), the reference CSVs' `bedca_food_id` column, the
suggestion index (`NameMatcher`), `FoodCategoriser` (956/957 names), `FoodState` (read off names),
`edible_portion` (gross measures), `/api/bedca`, the UI footer.

## Phases (ordered; removal is last)
- **A — Spike (S), done 2026-10-03:** `product/spikes/FD-033-A-composition-spike.md`. Combined 95 % of the
  210 ingredients have a same-food equivalent (CIQUAL 93 %, BLS 84 %), 0 with none; 77/80 BEDCA foods in use
  covered. Neither has an edible-portion factor (blocker for gross weights) nor FoodEx2/LanguaL; CIQUAL has
  145 foods with no energy (143 `-`, 2 `traces`); qualified values (`traces`, `<LOQ`, `-`) and decimal comma.
- **B — Import the replacement (M), design per spike §5 (adopted 2026-10-03):**
  - **One table** with `source` (`CIQUAL`/`BLS`) + `source_code` as published, `uk (source, source_code)`;
    every consumer points at one id column. Values + units as published.
  - **CIQUAL answers first, BLS fills gaps**; the crosswalk marks one preferred entry per Spanish name
    (CIQUAL by default, BLS where only it has the written state, e.g. `plancha`, `asado`).
  - **Spanish-name crosswalk** (`reference-data/composition-es/links.csv` or similar): `source,
    source_code, name_es, aliases, preferred, edible_portion, edible_portion_source, note`; fdiet's own
    work, **CC BY 4.0**, "changes made" note, **no BEDCA values**.
  - `name_es` in **BEDCA's head-first style** (`Pollo, pechuga, plancha`) so `NameMatcher`,
    `FoodCategoriser` and the `FoodState` reader keep working; source group codes only as a test cross-check.
  - **Machine prefill, human approval:** candidates pre-filled by matching BEDCA's `f_eng_name` against
    both tables' English names; a person approves every row.
  - **Crosswalk scope (B):** only the **81 distinct foods of `example-ui.xlsx` "Dieta 1" + the 80 BEDCA
    foods in use** (lists in spike §4). The rest → FD-036.
  - **CIQUAL foods without energy (145: 143 publish `-`, 2 publish `traces`):** kept in a tracked list in the repo beside the crosswalk,
    **flagged, never given invented energy**; `AlternativeService` must handle a null energy. Finding an
    open energy source → FD-037.
  - **Qualified values** (`traces`, `<LOQ`, `<LOD`, `-`, `< 0,2`, `TR`): stored as **null**, original text
    **not** kept — the committed upstream file is the record (user, 2026-10-03). Decimal comma parsed.
  - **Originals in Git LFS** (2026-10-03): the snapshots' `.xlsx` / `.pdf` (~17 MB; BLS table 14 MB) are
    tracked through LFS (`reference-data/composition/.gitattributes`; `git lfs install --local` done). A fresh
    clone needs git-lfs, else the files are pointers and the sync fails → requirement on FD-034.
  - **Snapshot in the repo:** the spike's downloads lived only in a job tmp dir and are lost — B commits
    the CIQUAL (Zenodo copy) and BLS files under FD-034's manifest rule (source page, DOI, retrieval date,
    SHA-256). BLS's zip URL token rotates, so record the page and DOI, not the link.
  - Search, suggestions, categoriser, state reader and nutrition work on it; selectable beside BEDCA
    (no removal yet). Footer names CIQUAL and BLS (and BEDCA until E); each food shows its source.
- **C — Re-key reference data (M):** every `bedca_food_id` in `reference-data/*/rations.csv`,
  `food_measures.csv` and the `ref_*` rows (incl. diet and global criteria) pointing at the new food;
  5 al día stays CC BY-SA with the change listed in its `LICENSE.md`.
  **Status 2026-10-03:** backend (V16) + frontend + tech-lead review done, **uncommitted**. Mapping approved by the
  user 2026-10-03 (decision 18). Left: backend marks CIQUAL 13716 `reviewed=true` and adds 1167→13716 to V16's
  mapping, tests green; then commit. Was waiting on approval of `product/spikes/FD-033-C-rekey-mapping.md` (1 LOW: CIQUAL 13716 piña en su jugo `reviewed=false`;
  4 MEDIUM: melón, nectarina, pomelo, tomate triturado) and it is committed. Follow-ups: FD-039–FD-042, FD-013.
- **D — Reset existing matches (S–M):** every `recipe_ingredients` / `extra_foods` `bedca_food_id` set to null
  (`raw_name` kept), re-matched by the nutritionist through the fix-up list with suggestions; before/after report of totals and match counts per diet.
  Notes from C review: `attachDietMeasure` must pass the ingredient's Spanish name (`foodName` null today);
  drop the wasted `criteria.dietRows` query for BEDCA foods; the composer's range measure becomes writable in
  units once ingredients carry composition ids. FD-039 (picked measure dropped on save) may be folded in here.
- **E — Remove (M):** also delete `IBedcaFoodService.suggest` / `entitiesAll`, `FoodSuggestionDto`; reword `FoodCategoriser` / `FoodState` comments off BEDCA. `bedca_foods.csv`, `BEDCA-ATTRIBUTION.txt`, the table, columns, FKs, `/api/bedca`,
  the BEDCA footer line; migration drops them. FD-041 (footer credits CIQUAL / BLS) may be done here. Verify no gated source anywhere; licence audit table.

## Acceptance criteria
- [ ] `reference-data/sources.csv` (or the FD-034 manifest) has a licence-audit view: every remaining
      source with an **SPDX id** (`CC-BY-4.0`, `CC-BY-SA-4.0`, `CC0-1.0`, `ODbL-1.0`, `etalab-2.0`, `OGL-UK-3.0`, …) and
      `commercial_use = true` for all. Exception: the AESAN reuse notice and US public domain have no SPDX id — name them plainly.
- [ ] `exchange_systems.csv` rows documented as **method-only** (a 10 g definition; no food list copied),
      published by fdiet under CC BY 4.0 crediting the method's authors,
      in `sources.csv` and `reference-data/README.md`.
- [ ] No file in the repo and no table in a fresh database holds BEDCA data: `bedca_foods.csv`,
      `BEDCA-ATTRIBUTION.txt`, `bedca_foods` and every `bedca_food_id` column gone.
- [ ] None of the gated sources in the audit table is in the repo or loaded (grep + listing in the story notes).
- [ ] Composer, fix-up panel and patient extras search the replacement by **Spanish** name
      (`lechuga`, `pan de molde`, `jamón serrano` find a food).
- [ ] Re-importing `example-ui.xlsx` matches at least as many ingredients outright as today — **41/210
      is the floor** (user, 2026-10-03). Outright matches come only from the crosswalk's Spanish names/aliases.
- [x] Crosswalk covers the 81 "Dieta 1" foods + the 80 BEDCA foods in use, every row human-approved, CC BY 4.0.
      (2026-10-03: all 122 rows of `reference-data/composition/composition-es/links.csv` approved by the user, `reviewed=true`.)
- [ ] The 145 CIQUAL foods with no energy are listed in a tracked repo file and flagged in the API; none
      has an invented energy; alternatives handle a null energy.
- [ ] Qualified values are null, never 0 or a guessed number.
- [ ] CIQUAL and BLS files committed with manifest rows (FD-034 rule); no install fetches them live.
- [ ] Every existing BEDCA match is reset to unmatched (`raw_name` kept) and the fix-up list offers
      suggestions from the new data — nothing is re-pointed automatically; counts reported.
- [ ] Every diet's totals before/after are reported; changes are visible, not silent.
- [ ] Ration counts, alternatives, yields and household measures work on the new data; a food without an
      edible-portion factor refuses a gross measure (blank beats wrong).
- [ ] Builder and patient footers show the new source's attribution, not BEDCA's.
- [ ] CLAUDE.md, `reference-data/README.md` updated; tests green; `pnpm build` clean.

## Tasks
- backend: spike (A); import + crosswalk + search/categoriser/state on new data (B); re-key reference CSVs
  and rows (C); re-match migration + before/after report (D); drop BEDCA schema/files/endpoint, audit (E).
  **Migrations in B, D and E** — restart backend on 5000 after each; `gradlew test` migrates the real
  `.env` DB, so back it up before D/E.
- frontend: food search and labels off BEDCA naming; attribution footer from the new source; any
  "BEDCA" wording in Spanish copy (B, E).
- tech-lead: review after each phase; E review checks nothing BEDCA-derived remains.

## Risks
- **Figures change for every diet**: French/German analyses replace Spanish ones (jamón serrano, chorizo,
  regional breads). Archived diets' totals change too.
- **No open source has Spanish names or Spanish measures** — the crosswalk is curated by hand; quality
  and upkeep are on us.
- **No edible-portion factor in either file** (spike §6.1): 16 `GROSS` rations in `aesan-mec-2010` and every
  gross household measure stop weighing unless the crosswalk supplies one (open question below).
- Spanish specifics stay proxies or unmatched: queso de Burgos, bacalao desalado, soja baja en sodio, néctar
  de ciruela, piña en su jugo. Mixed methods (protein factor, energy formula, BLS recipe-calculated).
- **Every match is lost on purpose** (reset, decision 2026-10-02): the nutritionist re-matches every week through the fix-up list; totals are partial until then.
- Categoriser / state / name-matcher were tuned to BEDCA's head-first names; new names need new rules.
- Global and diet criteria keyed on a BEDCA food may have no equivalent.
- Conflicts with the 2026-09-13 decision (BEDCA kept, non-commercial) and with the report's own advice
  (keep BEDCA while non-commercial, add CIQUAL beside it) — see decisions.md 2026-10-02.

## Decisions (user, 2026-10-02 — see decisions.md)
1. Replacement: **both CIQUAL 2025 and BLS 4.0**; spike A decides how they combine, not which wins.
2. Existing matches: **reset** to unmatched (`raw_name` kept), re-matched via the fix-up list. No crosswalk for old matches.
3. BEDCA runs until B–D are live; removed last (E).
4. Permission-gated sources out for good: FD-004, FD-006 dropped; FD-005 open sources only.
5. Spanish-name list and method-only definitions: **CC BY 4.0**, crediting the method's authors; no `LicenseRef-*`.

## Decisions (user, 2026-10-03 — phase B)
6. Crosswalk scope: the 81 "Dieta 1" foods + 80 BEDCA foods in use; the rest → FD-036.
7. 145 CIQUAL foods without energy (corrected 2026-10-03 from 143: 143 `-` + 2 `traces`): tracked list, flagged, no invented energy; open energy source → FD-037.
8. Qualified values → null (original text kept beside it: design default, backend to confirm). **Settled 2026-10-03 (decision 13): null, original text not kept.**
9. 41/210 outright matches stays the floor.
10. Spike §5 design adopted (one table, CIQUAL first then BLS, head-first `name_es`, prefill + approval).

11. (2026-10-03) Edible portion: crosswalk `edible_portion` = 1 − refuse%/100 from the closest **USDA FoodData
    Central SR Legacy** food (CC0), `fdc_id` kept per row; no sensible match → null, and null still refuses a
    gross-weight measure.

## Decisions (user, 2026-10-03 — phase B close-out)
12. Snapshot originals (`.xlsx`, `.pdf`) go through **Git LFS** (`reference-data/composition/.gitattributes`);
    a new installation needs git-lfs before cloning → requirement added to FD-034.
13. Qualified values stored as **null without the original text**; the committed upstream file is the record.
    Settles decision 8.
14. All **122 crosswalk rows approved** (`reviewed=true`). Caveat accepted: some USDA SR Legacy edible portions
    describe US as-purchased forms (nuts in shell 0.40–0.45, chicken bone-in 0.48) and were approved as-is.

## Decisions (user, 2026-10-03 — phase C)
15. **Replace** `bedca_food_id` with a composition-food key on `ref_rations` / `ref_food_measures` and the reference CSVs now;
    no BEDCA column beside it. Accepted: id-keyed rows match no BEDCA-matched ingredient until D.
16. BEDCA→CIQUAL/BLS pick per reference row: **machine prefill, user approves**; missing foods added to `links.csv`
    head-first, `reviewed=false` until approved.
17. Diet and global criteria re-keyed through the same approved mapping; no equivalent → reported, not guessed.
18. Mapping list `product/spikes/FD-033-C-rekey-mapping.md` approved as proposed (2026-10-03): piña en su jugo → CIQUAL 13716
    (row `reviewed=true`), melón → CIQUAL 13742, nectarina → CIQUAL 13148, pomelo → BLS F604100, tomate triturado → CIQUAL 20169.

## Decisions (user, 2026-10-03 — phase D)
19. After the reset, stored ingredients and extras are **re-matched by exact Spanish name/alias** against the
    crosswalk — the same rule a fresh import uses. Old BEDCA ids are never carried over; anything not exact
    stays unmatched with suggestions. Refines decision 2 (reset kept; no BEDCA→composition carry-over).
20. **Every consumer moves in D**: matching, totals, rations, measures, yields, compose, alternatives, journal
    extras. Phase E only deletes BEDCA.
21. **No new BEDCA matches in D**: API refuses `bedcaFoodId`; UI searches CIQUAL/BLS only.
22. Before/after report is a **one-off file** `product/reports/FD-033-D-before-after.md` from the dev DB.

## Phase D — refined 2026-10-03 (size L, one story; backend then frontend)
Defaults (proposed by PO, not asked): branded matches (`food_item_id`) untouched; `food_measure_id` kept and
re-chosen by the resolver where it no longer fits; FD-039 folded in; FD-041 (footer) stays in E; dev DB dumped
(`mysqldump`) before the migration; `bedca_food_id` columns stay (null) until E drops them.

Acceptance criteria:
- [x] D1 `recipe_ingredients` and `extra_foods` carry `composition_food_id` (FK); every `bedca_food_id` is null after migration; `raw_name` unchanged.
- [x] D2 Stored ingredients/extras re-matched by exact Spanish name/alias only (decision 19); counts reported per diet.
- [x] D3 Re-importing `example-ui.xlsx` matches ≥ 144/210 outright (≥ 41 floor); `ExampleDietCompositionMatchTest` still measures it.
- [x] D4 `PATCH …/ingredients/{id}` and `POST …/extras` take `compositionFoodId`; `bedcaFoodId` is a 400.
- [x] D5 Fix-up list suggestions, `POST /diets/parse`, `POST /diets/compose` and import come from composition foods.
- [x] D6 Totals, rations, household measures (incl. diet/global criteria naming a food), yields and alternatives work on composition ids; `byNameOnly` workaround gone; a food with null `edible_portion` refuses a gross measure.
- [x] D7 `/api/alternatives/{foodId}` and `/api/reference/{rations,measures,yields}` take composition ids only; `bedcaFoodId` param removed.
- [x] D8 FD-039: after a `PUT`, an ingredient keeps its picked `food_measure_id` while the row is still live for that measure.
- [x] D9 Report `product/reports/FD-033-D-before-after.md`: per diet, kcal + counted/unmatched/unmeasured before and after, and how many matches came back by exact name.
- [x] D10 UI: composer, fix-up panel, recipe library and patient extras search and show CIQUAL/BLS foods only (source label per food); no Spanish copy says BEDCA except the footer.
- [x] D11 CLAUDE.md updated; tests green; `pnpm build` clean; tech-lead review done.

Tasks: backend — migration (column, FK, reset, re-match) + all consumers + report (**migration: restart 5000 after;
`gradlew test` migrates the `.env` DB — dump first**). frontend — every food picker and label on composition foods.
tech-lead — review after both.

## Open questions
_None blocking. (Edible portion closed 2026-10-03 — decision 11.)_

## Hand-off prompts
### backend — phase D (2026-10-03)
FD-033 phase D, story `product/stories/FD-033-open-data-only.md` (section "Phase D — refined", decisions 19–22,
AC D1–D9, D11). Point recipe ingredients and journal extras at `composition_foods` and move every consumer off
BEDCA. Before any migration or `gradlew test`, `mysqldump` the `.env` database to a file outside the repo and
report its path. Migration: add `composition_food_id` (FK) to `recipe_ingredients` and `extra_foods`, null every
`bedca_food_id` (keep the columns; E drops them), keep `raw_name` and `food_item_id`. Re-match stored rows by exact
Spanish name/alias through the crosswalk only — same rule as import, never similarity, never BEDCA id carry-over
(decide whether that runs in the migration or a one-off startup/endpoint step; state which). Move matching, fix-up
suggestions, parse, compose, import, nutrition totals, rations, household measures (diet + global criteria now
reachable by food id; remove the `byNameOnly` / `bedcaFoodId` workarounds), yields, alternatives and journal extras
to composition ids; `bedcaFoodId` in requests becomes a 400. Fold in FD-039 (picked measure kept after `PUT`) and
the C-review notes in the story's phase D bullet. Capture each diet's totals and counts before the migration and
after, and write `product/reports/FD-033-D-before-after.md`. Update CLAUDE.md. Report: API changes for frontend
(every request/response field renamed or removed), outright match count on example-ui.xlsx, test count. List
out-of-scope bugs; do not fix them.

### frontend — phase D (send after backend reports)
FD-033 phase D, AC D10–D11 in `product/stories/FD-033-open-data-only.md`. Against the backend's reported API:
every food picker (composer, fix-up panel, recipe library, patient extras) searches and shows CIQUAL/BLS
composition foods only, with the source label; send `compositionFoodId` wherever `bedcaFoodId` was sent; drop
BEDCA wording from Spanish copy except the attribution footer (E). Remove `BedcaFoodSearch.vue` if nothing uses it.
`pnpm build` clean; check live on 5173 against the backend on 5000. List out-of-scope bugs; do not fix them.

### backend — phase A (sent by the coordinator 2026-10-02)
Spike only, no production code or migration: map the 210 `example-ui.xlsx` ingredients and every
`bedca_food_id` in use against CIQUAL 2025 and BLS 4.0. Report match rate per source and combined,
whether each has an edible-portion factor, FoodEx2/LanguaL codes, and units per value; propose how
the two combine (which answers first, how gaps fill, per food family).

## Notes
- Phase B status 2026-10-03: implemented, tech-lead reviewed (299 tests green), **uncommitted**. Dev DB still
  has `name_reviewed = false` until a re-sync (`POST /api/composition/sync`). Review follow-ups → FD-038.
- FD-034 (repeatable data loading) is the manifest/snapshot side; its composition snapshot waits on B.
- The datos.gob.es open-BEDCA request is unanswered since 2026-04-14; if AESAN ever opens BEDCA, revisit.
- Phase D status 2026-10-03: backend (V17 SQL + V18 Java), frontend and tech-lead review done, 337 tests green,
  `pnpm build` clean, **uncommitted**. Left: live check after the 5000 restart, then commit. V18 depends at compile
  time on `ExactNames`, `NameIndex`, `CompositionIndexRow`, `Texts`, `PortionSize` (rule cannot drift) — renaming
  them touches an applied migration. Follow-ups: FD-043–FD-051.
- Live check 2026-10-03 (5000 restarted, UI on 5173): D10 confirmed. Diet 9 shows 4600 kcal with 90 of 211 counted. The fix-up panel ranks CIQUAL candidates. The composer shows "CIQUAL 2025" in search and on the picked food. A patient extra logs against CIQUAL; the test extra was deleted. `bedcaFoodId` is a 400. No console errors. The only BEDCA text left is the footer (FD-041). The check found FD-052: a composed dry ration on `Lenteja, cocida` is counted as cooked.
- Phase D done 2026-10-04: committed by the user in 3ced939 with FD-048, FD-052 and FD-041 still open; they are fixed in the next sections, not in D.

## Decisions (user, 2026-10-04 — phase E)
23. `bedca_foods.csv` and `BEDCA-ATTRIBUTION.txt` are **deleted from the tree**; git history is **not** rewritten.
24. `bedcaFoodId` in any request **stays a 400** (`RetiredFields`) after removal.
25. The **licence audit is part of E** (not FD-034 A).

## Phase E — refined 2026-10-04 (size M; backend, then tech-lead; no frontend work: `UI/src` names BEDCA nowhere)
Acceptance criteria:
- [x] E1 Migration `V22` drops `recipe_ingredients.bedca_food_id`, `extra_foods.bedca_food_id` (with their FKs/indexes) and the `bedca_foods` table. A fresh database migrates V1→V22 with no BEDCA table or column.
- [x] E2 `/api/bedca` gone (404). `BedcaController`, `BedcaFoodService`, `BedcaImportService`, `BedcaFood`, their DTOs, mapper, repository, exception, `FoodSuggestionDto`, `IBedcaFoodService.suggest`/`entitiesAll`, the `fdiet.bedca.*` property and `BEDCA_CSV_PATH` removed; BEDCA-only tests removed.
- [x] E3 `bedcaFoodId` in any request is still a 400 (decision 24); its test stays.
- [x] E4 `bedca_foods.csv`, `BEDCA-ATTRIBUTION.txt` deleted from the tree (decision 23). Applied migrations (V4…V18) are not edited.
- [x] E5 Comments and Javadoc in `FoodCategoriser`, `FoodCategory`, `FoodState`, `Nutrient`, `CompositionFigures`, `NutritionService` describe the composition foods, not BEDCA.
- [x] E6 Licence audit: `reference-data/sources.csv` gains `spdx` and `commercial_use` columns (reader by header, sync still green); composition `manifest.csv` already has SPDX; `fooddata.csv` (AESAN branded) appears in the audit. Every source `commercial_use = true`; AESAN reuse notice and US public domain named plainly (no SPDX). `exchange_systems.csv` documented as method-only, CC BY 4.0, crediting the authors, in `sources.csv` and `reference-data/README.md`.
- [x] E7 Gated sources (SENC, DIAL, FINUT, Russolillo lists, Murillo/SJD, TABULA, Moreiras, ASPCAT, AEP 2018, FAO/INFOODS, NEVO, Frida, INSA) absent from repo and DB: grep + listing written into this story's notes.
- [x] E8 `ExampleDietCompositionMatchTest` still ≥ 144/210; all tests green; app starts on 5000 against the migrated dev DB.
- [x] E9 CLAUDE.md (BEDCA sections, migration table, data files) and `reference-data/README.md` updated; `plan.md` BEDCA mentions marked historical.
- [x] E10 tech-lead review: nothing BEDCA-derived remains in code, data or schema (grep `bedca` -i shows only migrations V4–V18, `RetiredFields` and history notes).

Tasks: backend — E1–E9 (**migration: `mysqldump` the `.env` DB first; `gradlew test` migrates it; restart 5000 after**). tech-lead — E10.

### Hand-off: backend — phase E (2026-10-04)
FD-033 phase E, story `product/stories/FD-033-open-data-only.md` (section "Phase E — refined", decisions 23–25, AC E1–E9). Remove BEDCA. Before any migration or `gradlew test`, `mysqldump` the `.env` database to a file outside the repo and report its path. Add `V22` dropping `recipe_ingredients.bedca_food_id`, `extra_foods.bedca_food_id` (FKs and indexes first) and the `bedca_foods` table; never edit an applied migration. Delete `/api/bedca` and every BEDCA class, DTO, test and property (`fdiet.bedca.*`, `BEDCA_CSV_PATH` in `.env.example`), `IBedcaFoodService.suggest`/`entitiesAll`, `FoodSuggestionDto`; check V18 and any Java migration still compile without them. Keep `RetiredFields` so `bedcaFoodId` stays a 400. `git rm` `bedca_foods.csv` and `BEDCA-ATTRIBUTION.txt` (no history rewrite; do not commit). Reword comments off BEDCA (E5). Licence audit (E6): add `spdx` and `commercial_use` columns to `reference-data/sources.csv` (reader by header; `POST /api/reference/sync` green), cover `fooddata.csv`, document `exchange_systems.csv` as method-only CC BY 4.0 in `sources.csv` and `reference-data/README.md`. Grep the repo for the gated sources (E7) and report the listing. Update CLAUDE.md (BEDCA sections, migration table, data files and licensing) and mark BEDCA mentions in `plan.md` as historical. Run all tests, confirm ≥ 144/210, restart the backend on 5000. Report: migration applied, dump path, files removed, test count, grep result for `bedca`. List out-of-scope bugs; do not fix them.

### Hand-off: tech-lead — phase E (after backend)
Review FD-033 phase E (uncommitted diff; AC E1–E10 in `product/stories/FD-033-open-data-only.md`). Check nothing BEDCA-derived remains in code, data, schema or docs except applied migrations, `RetiredFields` and history notes; V22 safe on a fresh DB and the dev DB; licence audit complete. Fix what you find, run all tests. List out-of-scope bugs as new-story candidates; do not fix them.

## Phase E — backend done 2026-10-04 (uncommitted; deletions staged by `git rm`)
- Dump: `C:\Users\victo\fdiet-backups\fdiet-before-V22-20261004.sql` (17.4 MB). V22 applied to dev DB 2026-10-04 20:48; fresh V1→V22 checked on a temp schema (dropped). 372 tests green; 144/210 holds; 5000 restarted (`/api/bedca` 404, `bedcaFoodId` 400).
- `fooddata.csv` is in the audit table of `reference-data/README.md`, not `sources.csv` (that file feeds `ref_sources`, which the UI lists as reference sources). New `ReferenceLicenceAuditTest`.
- E7 listing: DB `ref_sources` = AESAN-2022-007, AESAN-MEC-2010, 5ALDIA-2019, FUNDACION-DIABETES-HC, RUSSOLILLO-MARQUES-2011, USDA-YIELDS-2014; no gated table. Repo hits are mentions only: SENC (a comparison note in `aesan-2022/food_measures.csv`, a V8 comment, a fictional code in `ReferenceMatcherTest`), INFOODS (CIQUAL tag vocabulary in `manifest.csv`), SJD (substring in BLS xlsx cells), Russolillo (5 al día authors; method-only 10 g definition). DIAL, FINUT, Murillo, TABULA, Moreiras, ASPCAT, AEP 2018, NEVO, Frida, INSA only in docs/research.
- `bedca` grep left: applied migrations V4–V18, V22, `RetiredFields` + request DTOs/doc strings, history notes in tests and docs, `reports/`, `research_notes/`, `.claude/agents/*.md` + backend skill (→ FD-074).
- Follow-ups: FD-074–FD-077.
- Tech-lead review 2026-10-04 (E10): no bug. Fixed comments still stating BEDCA facts as current (`NutrientValue`, `NutrientDto`, `NutritionService`, `DataReader`, `NutritionSimilarity`, CLAUDE.md:78); added `everyCompositionFileCarriesAnOpenSpdxId` (15 manifest rows). 373 tests, 144/210. Open: the audit says AESAN notice "commercial use: yes" while the README still says the notice page was a 404 → FD-078.
