# Nutrition reference data

Rations, household measures, recommendations and meal energy shares, loaded into the `ref_*` tables
at startup and by `POST /api/reference/sync`. Every row names its source and the page it was read
from, and every figure was checked against the original document. The research behind the choice of
sources, and the conflicts between them, is in `plan.md`.

## Layout

| File | What it holds |
| --- | --- |
| `sources.csv` | every document, its licence class, the attribution its figures need, and the licence audit: `spdx` (an SPDX id, or the licence named plainly where it has none) and `commercial_use` |
| `exchange_systems.csv` | exchange unit definitions (no food lists): method-only, fdiet's own work under CC BY 4.0 |
| `<source>/populations.csv` | the source's population bands, labelled as the source labels them. A selectable band is a profile a diet can be written against |
| `<source>/rations.csv` | one standard serving per row, for one band |
| `<source>/food_measures.csv` | what a household measure of a food weighs |
| `<source>/recommendations.csv` | how many rations of which groups, per day or per week |
| `<source>/meal_shares.csv` | the share of the day's energy per meal slot |
| `<source>/yield_factors.csv` | cooking yields: cooked grams per 100 g raw, by food and method |

One folder per source, so a licence stays with its own figures.

`composition/` is not part of this layer: it holds the open food composition tables (CIQUAL 2025,
BLS 4.0), fdiet's Spanish-name crosswalk and their manifest, loaded into `composition_foods` by
`POST /api/composition/sync`. See `composition/README.md`. The reference sync reads none of its files.

## What is loaded, and under what terms

| Folder | Source | Licence |
| --- | --- | --- |
| `aesan-2022/` | AESAN-2022-007, recomendaciones dietéticas sostenibles, pp. 50-53 | AESAN legal notice: reuse with source and date (re-verify, the notice page returned 404) |
| `aesan-mec-2010/` | AESAN/MEC 2010 school consensus, Anexo II (pp. 13-14) and Anexo III (p. 15) | same AESAN notice |
| `5aldia-2019/` | Russolillo et al. 2019, Tabla 4 (pp. 214-215) | **CC BY-SA 4.0**; see `5aldia-2019/LICENSE.md` |
| `usda-yields-2014/` | USDA Table of Cooking Yields for Meat and Poultry, Release 2 (2014), a selection | US public domain; see `usda-yields-2014/LICENSE.md` |
| `exchange_systems.csv` | the diabetes carbohydrate ration (10 g, Fundación para la Diabetes Novo Nordisk) and the general 10 g exchanges (Russolillo & Marques-Lopes 2011) | **method-only**: the 10 g definition of each unit and nothing else — neither food table is copied, every count is computed from the open composition tables. Published by fdiet under **CC BY 4.0**, crediting the method's authors (decision of 2026-10-02) |

Not loaded, and not to be: a written permission is not an open licence (decision of 2026-10-02), so
these stay out of the repository and the database — SENC 2018 annexes, DIAL's household measures, the
FINUT photographic guide, the Russolillo & Marques-Lopes exchange lists, the Murillo and Sant Joan de
Déu diabetes tables, TABULA, Moreiras, ASPCAT (CC BY-NC-ND), AEP 2018, the FAO/INFOODS density
database (NC-SA), NEVO, Frida and INSA. See `plan.md` §5 and
`reports/Open food data and permissions.md`.

## Licence audit (FD-033 phase E, 2026-10-04)

Every dataset in the repository, with its licence and whether commercial use is allowed. An SPDX id
where one exists; the AESAN reuse notice and US public domain have none and are named plainly.
`ReferenceLicenceAuditTest` keeps `sources.csv` true; `composition/manifest.csv` carries the SPDX id
of every composition file.

| Dataset | Where | Licence (SPDX) | Commercial use |
| --- | --- | --- | --- |
| AESAN 2022 branded products ("Datos de composición de alimentos y bebidas comercializados en España en 2022", label data collected by Kantar Worldpanel) | `fooddata.csv` → `food_items` | AESAN reuse notice (aviso legal; Ley 37/2007): cite the source and the date of last update, do not alter the content | yes |
| AESAN-2022-007, AESAN/MEC 2010 | `aesan-2022/`, `aesan-mec-2010/` | AESAN reuse notice (aviso legal; Ley 37/2007) | yes |
| 5 al día 2019 | `5aldia-2019/` | `CC-BY-SA-4.0` | yes |
| USDA cooking yields 2014 | `usda-yields-2014/` | US public domain (17 U.S.C. § 105) | yes |
| Diabetes HC ration, Russolillo & Marques-Lopes exchanges (method-only definitions) | `exchange_systems.csv` | `CC-BY-4.0` (fdiet's, crediting the authors) | yes |
| CIQUAL 2025 (ANSES) | `composition/ciqual-2025/` | `CC-BY-4.0` | yes |
| BLS 4.0 (Max Rubner-Institut) | `composition/bls-4.0/` | `CC-BY-4.0` | yes |
| USDA SR Legacy refuse extract | `composition/usda-sr-legacy/` | `CC0-1.0` | yes |
| fdiet's Spanish-name crosswalk | `composition/composition-es/` | `CC-BY-4.0` | yes |

BEDCA, the one non-commercial source the project ever held, was removed in FD-033 phase E
(`bedca_foods.csv`, `BEDCA-ATTRIBUTION.txt`, the `bedca_foods` table and every `bedca_food_id`
column; migration `V22`).

## Conventions

- **Ranges stay ranges.** `grams_min` and `grams_max` are both filled; a point is written as the same
  value twice. A measure with a range weighs nothing until a nutritionist gives the diet a value.
- **State and basis are what the source wrote.** `state`: `RAW`, `DRY` (en seco), `COOKED`, `CANNED`,
  `DRAINED`, `UNSPECIFIED`. `weight_basis`: `NET_EDIBLE`, `GROSS`, `UNSPECIFIED`.
- **Which foods a row covers.** `composition_source` + `composition_code` name one food of the open
  composition tables (`CIQUAL` + its `alim_code`, or `BLS` + its BLS code) — the key each source
  publishes, because `composition_foods.id` differs per installation. The sync resolves them in one
  lookup; a row whose food is not loaded (`POST /api/composition/sync` first) is skipped with a reason.
  Every food named must have a row in `composition/composition-es/links.csv`, which gives it the
  Spanish name its family is read from (`ReferenceCompositionKeysTest` checks it). Otherwise
  `food_category` (fdiet's food family, read off the food's Spanish name) narrowed by `keywords`:
  phrases separated by `;`, without accents, each word allowed its plural; the longest matching phrase
  wins; a phrase starting with `!` excludes (`yogur;!liquido`). A row naming a food covers that
  composition food only. (History: until 2026-10-03 rows named a food of the retired BEDCA table;
  FD-033 phase C re-keyed them, and `product/spikes/FD-033-C-rekey-mapping.md` lists every pick.)
- **Millilitres are read as grams**, the one assumption `PortionScaler` makes for every liquid.
  For olive oil that overstates the weight by about 9 % (0.91 g/ml).
- `group_code` ties a ration to the recommendations that count it.
- Codes are stable keys. A re-sync writes over a known code and keeps its id, so a diet pointing at a
  measure keeps pointing at it. Removing a row from a CSV does not delete it from the database.
