# Nutrition reference data

Rations, household measures, recommendations and meal energy shares, loaded into the `ref_*` tables
at startup and by `POST /api/reference/sync`. Every row names its source and the page it was read
from, and every figure was checked against the original document. The research behind the choice of
sources, and the conflicts between them, is in `plan.md`.

## Layout

| File | What it holds |
| --- | --- |
| `sources.csv` | every document, its licence class and the attribution its figures need |
| `exchange_systems.csv` | exchange unit definitions (no food lists) |
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
| `exchange_systems.csv` | the diabetes carbohydrate ration (10 g) and the general 10 g exchanges (Russolillo & Marques-Lopes 2011) | definitions only; neither food table is copied |

Not loaded, because permission is needed first: SENC 2018 annexes, DIAL's household measures, the
FINUT photographic guide, the Russolillo & Marques-Lopes exchange lists, the FAO/INFOODS density
database. See `plan.md` §5.

## Conventions

- **Ranges stay ranges.** `grams_min` and `grams_max` are both filled; a point is written as the same
  value twice. A measure with a range weighs nothing until a nutritionist gives the diet a value.
- **State and basis are what the source wrote.** `state`: `RAW`, `DRY` (en seco), `COOKED`, `CANNED`,
  `DRAINED`, `UNSPECIFIED`. `weight_basis`: `NET_EDIBLE`, `GROSS`, `UNSPECIFIED`.
- **Which foods a row covers.** `bedca_food_id` names one food. Otherwise `food_category` (fdiet's
  food family, read off the BEDCA name) narrowed by `keywords`: phrases separated by `;`, without
  accents, each word allowed its plural; the longest matching phrase wins; a phrase starting with `!`
  excludes (`yogur;!liquido`).
- **Millilitres are read as grams**, the one assumption `PortionScaler` makes for every liquid.
  For olive oil that overstates the weight by about 9 % (0.91 g/ml).
- `group_code` ties a ration to the recommendations that count it.
- Codes are stable keys. A re-sync writes over a known code and keeps its id, so a diet pointing at a
  measure keeps pointing at it. Removing a row from a CSV does not delete it from the database.
