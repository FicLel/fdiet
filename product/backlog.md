# Backlog

Highest priority first. Seeded 2026-10-02 from `plan.md` ("Not done yet") and earlier notes;
**not yet prioritised with the user** — the order below is provisional.

| Id | Story | For | Size | Status |
| --- | --- | --- | --- | --- |
| FD-001 | Read a bracket state that contradicts the dish name (`pechuga a la plancha (150 g en crudo)`) so the state mismatch and yield hint fire on the commonest writing | nutritionist | M | idea — needs a decision on which wins |
| FD-002 | Alternatives screen in the UI (`basis`, `profile`, equivalent grams) — today API-only | nutritionist | M | idea |
| FD-003 | Patient goal ("Objetivo") — the last placeholder on the patient screen, same for everyone | both | M | idea |
| FD-005 | Phase 5 — special populations: paediatric 0–3, older adults, pregnancy/lactation — open sources only (ASPCAT 2022 NC-ND and AEP 2018 out, 2026-10-02) | nutritionist | L | idea — documents to read first |
| FD-007 | `UI/README.md` still the Vite scaffold — document the UI | dev | S | idea |
| FD-008 | Housekeeping: drop the `fdiet_ui_verify` throwaway schema | dev | S | waiting on user |
| FD-010 | Patient-friendly food names on the plate (`Huevo` not `Huevo, entero, crudo`) | patient | S | idea — split from FD-009 |
| FD-011 | Bug: deleting a diet's own measure criterion silently unweighs the ingredients using it (`DELETE /api/diets/{id}/measures/{mid}`, `MeasureCriterionService.java:109`) — refuse like the global delete ; also the global delete checks usage without a lock (`deleteGlobal` ~L205), low risk | nutritionist | S | idea — found in FD-009 |
| FD-012 | Bug: `POST /api/diets/compose` with an unknown `foodMeasureId` answers 400, should be 404 (`DietService.java:378`) | dev | S | idea — found in FD-009 |
| FD-013 | Split oversized files: `ReferenceService` 676, `DietService` 624, `RecipeService` 575, `RecipeLibraryDialog.vue` 512, `ExtraFoodPanel.vue` 760 (+ hard-coded colours `#f1f4f2`, `#c2ccc6`, `rgba(22, 28, 24, 0.1)` → tokens), `stores/foodLink.ts` 519, `UI/src/api/types.ts` 644 lines (limit 500); drop `IReferenceService` diet-measure pass-throughs (inject `IMeasureCriterionService` in `DietService`); `RationComposer.vue` 492 (move amount block to `ComposerAmount.vue`); one `failure(what, cause)` helper for ~25 repeated error messages | dev | M | idea — found in FD-009 |
| FD-014 | Bug (UI): size word always feminine — `measureText` writes "vaso mediana", "plato pequeña" (`UI/src/domain/rations.ts:52`); extras say "g cada una" for any measure (`ExtraFoodRow.vue:31`) | nutritionist | S | idea — found in FD-009 |
| FD-015 | Fix-up panel (`FoodLinkPanel.vue:207,214`) still disables range measures and saves only diet criteria — offer the global criterion like the composer; its weight form prints the raw unit (`Peso de «unidades»`, `1 unidades =`, lines 178–248) | nutritionist | S | idea — found in FD-009 |
| FD-016 | Composer writes a fractional count oddly: `0.5 unidad mediana` (dot decimal, singular) in the cell (`DietService.compose`, `count > 1`) | nutritionist | S | idea — found in FD-009 |
| FD-017 | A screen listing all the nutritionist's global unit weights (edit / delete / usage) — today only per food inside the composer (`GET /api/reference/criteria` exists, unused) | nutritionist | S | idea — leftover of FD-009 AC 8 |
| FD-018 | A new global unit weight does not reach ingredients already stored (`2 huevos medianos` in existing weeks stays unweighed until republished); `PUT /diets/{id}/measures` does attach (`MeasureCriterionService.createGlobal` ~L170) | nutritionist | S | idea — found in FD-009 review |
| FD-019 | Bug: patient sees no amount for `1 kiwi` on a plate served 2 (`isUnstatedQuantity` ignores servings, `UI/src/domain/dishText.ts:93`) — predates FD-009 | patient | S | idea — found in FD-009 review |
| FD-021 | Bug (UI): measure label repeats its range and mixes dashes — `1 huevo mediano (53-63 g) · 53–63 g` (`measureText` appends weight to `householdText`) | nutritionist | S | idea — found in FD-009 rework |
| FD-022 | Bug: AESAN `1 huevo mediano` offered for yolk, white and dried yolk (keyword `huevo` reaches `Huevo de gallina, yema/clara…`) — needs keyword exclusions | nutritionist | S | idea — found in FD-009 check |
| FD-024 | Deleting an unused unit weight in the composer has no confirmation step | nutritionist | S | idea — found in FD-009 review |
| FD-025 | Private recipes deleted row by row (`RecipeService.deletePrivate` uses JPA `deleteAll`): statements grow with ingredients on diet delete and every `PUT /api/diets/{id}`; one bulk delete by id would do (schema cascades ingredients) | dev | S | idea — found in FD-023 |
| FD-026 | Bug (data): archived diet 5 (patient 1) ends before it starts (`startedOn 2026-09-01`, `endedOn 2026-08-29`) — find how an archive can write that and guard it | dev | S | idea — found in FD-023 |
| FD-027 | Patient screen `/mi-dieta` lets anyone add, edit and delete patients from its selector — decide if the patient view should only switch patients | patient | S | idea — found in FD-023; needs a product decision |
| FD-028 | Bug (UI): `/mi-dieta` reloads its week only on first visit or patient switch, so after a publish in the builder it shows an old week | patient | S | idea — found in FD-023 |
| FD-029 | Bug (UI): diet list can show a stale or missing "En vigor" row with no error when `/api/diets/current` fails (`patientDiets.ts` `load()` uses `refreshCurrent()`, which swallows errors) | nutritionist | S | idea — found in FD-023 review |
| FD-030 | Synonyms in food search (`york` → `Jamón cocido`, `AOVE` → aceite de oliva virgen extra) — who maintains the list is open | nutritionist | S | idea — split from FD-020 |
| FD-031 | Ranking favours shorter names: `jamón` puts `Jamón asado`/`serrano` (50) above `Jamón cocido, …` (33); `suggest` javadoc claims ties go to the more specific name but code breaks ties alphabetically — decide wanted order (search + fix-up suggestions); also the as-typed tail is alphabetical, so `té` buries `Té` below `Aceite…`, `Leche…` (proposal: exact, then starts-with, then rest) | nutritionist | S | idea — found in FD-020 backend; needs a product decision |
| FD-032 | Bug (UI): patient extras panel shows "El catálogo no tiene nada con ese nombre…" and "Añadir «…» sin vincular" under a search error (`ExtraFoodPanel.vue` `v-if` ignores `searchError`) | patient | S | idea — found in FD-020 frontend |
| FD-033 | Open data only: CIQUAL 2025 + BLS 4.0 replace BEDCA (Spanish-name list, CC BY 4.0), re-key reference rows, reset existing matches, then remove BEDCA; licence audit with SPDX ids. Epic, phases A–E | both | L | in progress — A done; B implemented + reviewed 2026-10-03, uncommitted |
| FD-034 | Repeatable data load: every open dataset as a hashed CSV snapshot + manifest (URL, Wayback, date, SHA-256, SPDX, attribution); one deterministic idempotent fresh-install load; offline/online check script. Phases A–C now, D after FD-033 B | dev | L | refining — questions answered; phase A splittable now |
| FD-035 | Deposit the redistributable data snapshot on Zenodo (DOI) and Software Heritage (SWHID); cite both in the README | dev | S | idea — after FD-034 |
| FD-036 | Crosswalk extension: Spanish head-first names (prefill + human approval) for the rest of CIQUAL 2025 / BLS 4.0 beyond FD-033's 161 foods, so search finds any food in Spanish | nutritionist | M | idea — after FD-033 B |
| FD-037 | Open energy source for the 145 CIQUAL 2025 foods (143 `-`, 2 `traces`) published without energy (BLS, USDA FDC CC0, …), cited per food; flagged and null until then | nutritionist | S | idea — after FD-033 B |
| FD-038 | Composition sync hardening (FD-033 B review follow-ups): startup sync logs no stack trace; an over-long alias fails with an SQL error instead of a 422; the upsert update path has not run against MySQL | dev | S | idea — from FD-033 B review |
