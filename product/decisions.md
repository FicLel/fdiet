# Product decisions

Append-only. Newest at the bottom. A reversed decision gets a new entry naming the one it replaces.

## Before this file existed (recorded 2026-10-02 from CLAUDE.md, plan.md and earlier sessions)

- **2026-08-30** — Bespoke CSS, no component library; the UI talks to the live API.
- **2026-08-30** — The patient's side (scores, off-plan food) gets a real backend: the `journal` context.
- **2026-08-31** — Multiple patients, **no authentication**; every patient is visible to anyone. A patient is a name.
- **2026-09-13** — fdiet stays open source forever; BEDCA data stays non-commercial regardless. (plan.md Q1)
- **2026-09-13** — AESAN 2022 is the default adult reference profile. (Q2)
- **2026-09-13** — A nutritionist may override a measure's weight for one diet, labelled as their criterion. (Q3)
- **2026-09-13** — Adult meal energy split is borrowed from the AESAN/MEC 2010 school document, with a label. (Q4)
- **2026-09-13** — Clinical exchanges (diabetes 10 g HC) sit behind a `clinical` flag on the diet. (Q5)
- **2026-09-13** — Patients may carry birth date and sex. (Q6)
- **2026-09-28** — A dish is a description plus a recipe; library recipes are linked and shared by all patients, edits are live; imported cells become private recipes; per-patient portions go through `servings`.

## 2026-10-02 — FD-009 units in the builder

- **2026-10-02** — A published range (egg 53–63 g) is never weighed silently: the nutritionist confirms a weight per unit once (midpoint offered as a proposal) and it is remembered.
- **2026-10-02** — That weight is the nutritionist's **global** criterion, reused across every diet, patient and library recipe, labelled as hers. A diet's own criterion still wins over it.
- **2026-10-02** — The nutritionist may create a unit no source publishes (`1 rebanada = 30 g`).
- **2026-10-02** — The patient reads units only (`2 unidades medianas`); grams stay on the nutritionist's side.

## 2026-10-02 — FD-023 deleting a diet

- **2026-10-02** — Any diet may be deleted, active or archived, after a confirm that states the journal it takes with it. Reason: test diets and bad imports must go; the nutritionist decides.
- **2026-10-02** — Deleting the active diet leaves the patient with no diet in force; no archived diet is reactivated silently.
- **2026-10-02** — Patient delete stays refused while diets exist (2026-08-31 design kept); diets are deleted one by one first.

## 2026-10-02 — FD-020 food search

- **2026-10-02** — BEDCA search is word-wise and ranked in all three boxes (composer, fix-up panel, patient extras). Reason: one behaviour everywhere.
- **2026-10-02** — A food matching any word of the term shows, ranked by words matched. Reason: an all-words rule leaves `jamón york` empty.
- **2026-10-02** — Synonyms deferred to their own story (FD-030). Reason: keeps FD-020 small; the list's owner is undecided.

## 2026-10-02 — FD-033 / FD-034 open data

- **2026-10-02** — Only openly licensed data (CC0/PD, CC BY, CC BY-SA, ODbL, Etalab 2.0, OGL, NLOD, AESAN reuse notice) in the repo and the app; anything else is removed. Replaces the 2026-09-13 entry "BEDCA data stays non-commercial regardless" (BEDCA goes, once an open replacement is in). Reason: user's call — fdiet must be reusable without asking anyone. Note: the research report advised keeping BEDCA beside CIQUAL while non-commercial; the user chose removal. Open: which replacement, what happens to existing matches, whether permission-gated sources are dropped for good (FD-033 Q1–Q4).
- **2026-10-02** — Every dataset is snapshotted in the repo with a hashed manifest; no install depends on a live URL. Reason: upstream licence/data pages already 404.
- **2026-10-02** — Composition replacement is **both CIQUAL 2025 and BLS 4.0** (CC BY 4.0); the FD-033 spike decides how they combine (which answers first, how gaps fill), not which wins.
- **2026-10-02** — Existing matches are **reset**: every `bedca_food_id` on `recipe_ingredients` / `extra_foods` becomes unmatched (`raw_name` kept) and is re-matched through the fix-up list with suggestions. No reviewed crosswalk for old matches. Reason: simpler; the machine offers, the nutritionist decides.
- **2026-10-02** — BEDCA keeps running until FD-033 phases B–D are live; it is removed last.
- **2026-10-02** — A written permission is not an open licence: permission-gated sources are out for good. FD-006 and FD-004 dropped; FD-005 limited to open sources (ASPCAT 2022, AEP 2018 out). Replaces the 2026-09-13 stance that SENC, DIAL, FINUT and Russolillo lists "wait on permission".
- **2026-10-02** — fdiet's Spanish-name list and its method-only definitions (`exchange_systems.csv`) are published under **CC BY 4.0**, crediting the method's authors; no `LicenseRef-*` ids. Reason: the easiest licence to understand.
- **2026-10-02** — FD-034: the branded catalogue auto-loads only when its table is empty; the check script runs by hand, its offline checksum mode also in `gradlew test`; Zenodo / Software Heritage later, as its own story.

## 2026-10-03 — FD-033 phase B (after the spike)

- **2026-10-03** — Spike design adopted: one composition table with `source` + `source_code`; CIQUAL answers first, BLS fills gaps; Spanish names in a fdiet-owned CC BY 4.0 crosswalk, written head-first like BEDCA; machine prefill from BEDCA's English names, a person approves every row. Reason: keeps one id column for every consumer and keeps the name-based matcher, categoriser and state reader working.
- **2026-10-03** — Crosswalk scope for phase B: the 81 foods of `example-ui.xlsx` "Dieta 1" + the 80 BEDCA foods in use. The rest is FD-036. Reason: covers what diets use now; grows on demand.
- **2026-10-03** — The 143 CIQUAL foods without energy are kept in a tracked list and flagged; no invented energy. Finding an open energy source is FD-037. Reason: a blank beats a wrong number.
- **2026-10-03** — Qualified values (traces, <LOQ, <LOD, -, < 0,2, TR) are stored as null; keeping the original text beside it is the design default, for backend to confirm.
- **2026-10-03** — 41/210 outright matches on `example-ui.xlsx` stays the acceptance floor.
- **2026-10-03** — Crosswalk `edible_portion` is filled from **USDA FoodData Central SR Legacy** refuse values (CC0 public domain): `edible_portion = 1 − refuse%/100` from the closest SR Legacy food, its `fdc_id` kept per row. No sensible match → null, and null still refuses a gross-weight measure. Reason: neither CIQUAL nor BLS publishes an edible-portion factor (spike §6.1); a blank beats a wrong number. Closes FD-033's edible-portion question.

## 2026-10-03 — FD-033 phase B close-out

- **2026-10-03** — Composition snapshot originals (`.xlsx`, `.pdf`, ~17 MB; BLS table 14 MB) are stored through **Git LFS** (rule in `reference-data/composition/.gitattributes`). A new installation needs git-lfs before cloning (requirement on FD-034). Reason: large binaries committed without bloating plain git history.
- **2026-10-03** — Qualified values (traces, <LOQ, -, TR…) are stored as **null without keeping the original text**; the committed upstream file is the record. Replaces the 2026-10-03 design default "original text kept beside it".
- **2026-10-03** — All 122 crosswalk rows (`reference-data/composition/composition-es/links.csv`) approved by the user. Caveat accepted: some USDA SR Legacy edible portions describe US as-purchased forms (nuts in shell 0.40–0.45, chicken bone-in 0.48), approved as-is.
- **2026-10-03** — Correction to the 2026-10-03 entry on foods without energy: **145** CIQUAL foods, not 143 (143 publish `-`, 2 publish `traces`).
