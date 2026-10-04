# FD-033 phase D — every diet before and after the move to CIQUAL / BLS

One-off report (decision 22), taken from the dev database on 2026-10-03.

- **Before:** `GET /api/diets/{id}` on the running backend (V16, ingredients matched to BEDCA), just
  before the migration. Database dump taken first:
  `C:\Users\victo\fdiet-backups\fdiet-before-FD-033-D-20261003.sql` (17 MB, outside the repo).
- **Migration:** `V17__point_ingredients_and_extras_at_composition_foods.sql` (adds
  `composition_food_id`, resets every `bedca_food_id` to NULL) and the Java migration
  `V18__rematch_ingredients_by_composition_name` (re-matches by exact Spanish name or alias of the
  crosswalk, once more without size words — the rule a fresh import uses; no similarity, no BEDCA id
  carried over). Applied 2026-10-03 19:06.
- **After:** the same `GET /api/diets/{id}` on the new build, right after the migration.

## Headline

| | Before (BEDCA) | After (CIQUAL / BLS) |
| --- | --- | --- |
| Ingredient rows, all diets | 959 | 959 |
| Matched to a generic food | 158 (BEDCA) | **452** (CIQUAL / BLS, all by exact name) |
| Branded matches (`food_item_id`, untouched) | 1 | 1 |
| Extras (`extra_foods`) | 0 rows | 0 rows |
| Household measures attached / released | 0 / — | 0 / 0 |
| Counted in the week totals (sum over diets, per serving) | 134 | 390 |
| Unmatched (sum over diets, per serving) | 800 | 506 |

Every BEDCA match was reset; 452 rows came back by exact Spanish name or alias. Of the 158 rows
BEDCA had matched, 37 did not come back (their names are not in the crosswalk); 331 rows that BEDCA
never matched now are.

## Per diet

Counts are the week summary's: `counted / unmatched / unmeasured` over the week's ingredient servings
(`ingredients`). kcal is the week total over the counted ones only — a partial total, as before.
"Matched" is the number of ingredient rows of the week pointing at a generic food: BEDCA before,
CIQUAL / BLS after (every one of them by exact name). "Lost" = matched before, unmatched after;
"Gained" = unmatched before, matched after.

| Diet | Patient | Name | Status | Ingredients | kcal before | kcal after | counted / unmatched / unmeasured before | after | Matched before | Matched after (exact name) | Lost | Gained | Branded |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 4 | Victor | Dieta 1 | ARCHIVED | 210 | — | 4588.42 | 0 / 209 / 1 | 89 / 105 / 16 | 0 | 104 | 0 | 104 | 1 |
| 5 | Victor | Semana de prueba (revisada) | ARCHIVED | 2 | — | 365.40 | 0 / 2 / 0 | 2 / 0 / 0 | 0 | 2 | 0 | 2 | 0 |
| 6 | Victor | Postoperatoria | ARCHIVED | 110 | — | 1153.72 | 0 / 110 / 0 | 27 / 82 / 1 | 0 | 28 | 0 | 28 | 0 |
| 7 | Victor | Dieta 1 | ARCHIVED | 210 | 1616.23 | 4600.18 | 32 / 177 / 1 | 90 / 105 / 15 | 33 | 105 | 1 | 73 | 0 |
| 8 | Victor | Semana de prueba | ARCHIVED | 4 | 292.26 | 30.96 | 3 / 1 / 0 | 2 / 2 / 0 | 3 | 2 | 1 | 0 | 0 |
| 9 | Victor | Dieta 1 | **ACTIVE** | 211 | 3054.13 | 4600.18 | 49 / 151 / 11 | 90 / 106 / 15 | 60 | 105 | 17 | 62 | 0 |
| 12 | Prueba 1 | Dieta 1 | ARCHIVED | 211 | 3135.84 | 4600.18 | 50 / 150 / 11 | 90 / 106 / 15 | 61 | 105 | 18 | 62 | 0 |
| 13 | Prueba 1 | Dieta de Prueba 2 | **ACTIVE** | 1 | — | — | 0 / 0 / 1 | 0 / 0 / 1 | 1 | 1 | 0 | 0 | 0 |

## Reading it

- **The figures changed for every diet, on purpose.** French and German analyses replace Spanish
  ones, and more of each week is now counted, so a total that rose is mostly *more of the week being
  counted*, not the same foods weighing more. Archived diets changed too (risk accepted in the story).
- **Diet 8 lost most of its total** (292 → 31 kcal): one of its three BEDCA matches has no crosswalk
  name and is now unmatched. It is in the fix-up list with suggestions.
- **The "Dieta 1" weeks match 104–105 rows, not the 144 a fresh import of example-ui.xlsx matches.**
  Their names were stored by an older parser that kept the quantity in the name (`1 cdta AOVE` ×22,
  `1 kiwi`, `1 melocotón`, `2 huevos`, `10 almendras`). An exact-name rule cannot see through that and
  must not guess; those rows are unmatched and the fix-up list offers the right food first. Re-importing
  the workbook today matches 144 of 210 (`ExampleDietCompositionMatchTest`).
- `unmeasured` grew (11 → 15) because more ingredients are matched and some are written in a unit
  nothing weighs yet (`1 kiwi`-style units without a measure, ranges). They count nowhere — a blank,
  not a guess.
- No household measure was attached to any stored ingredient before, so none was released.
