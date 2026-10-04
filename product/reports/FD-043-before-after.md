# FD-043 — every diet before and after re-reading the stored recipe texts

One-off report, taken from the dev database on 2026-10-04.

- **Before:** `GET /api/diets/{id}` on a backend built from the working tree (V20 applied, FD-052,
  FD-054 and FD-060 in), just before the migration. No database dump was taken (no `mysqldump` on this
  machine); the before JSON of every diet is the record of the rows.
- **Migration:** the Java migration `V21__reread_recipe_texts`, applied 2026-10-04 20:14 by
  `gradlew build` (`FdietApplicationTests`). It re-reads every recipe's `raw_text` with today's
  `MealTextParser`, rewrites name, quantity, range, unit, state and size, keeps every food match and
  picked measure (paired by position), re-chooses a rule-chosen measure when the question changed, and
  re-matches the unmatched by exact Spanish name or alias (V18's rule).
- **After:** the same `GET /api/diets/{id}` on the new build, right after the migration.

## Headline

| | Before | After |
| --- | --- | --- |
| Recipes in the database | 301 | 301 |
| Recipes with `raw_text` (re-read) | 3 | 3 |
| Recipes without `raw_text` (untouched) | 298 | 298 |
| Recipes left untouched because the re-read gives another number of ingredients | — | **0** |
| Ingredient rows re-read | — | 11 |
| Rows changed | — | 2 |
| Re-matched by exact Spanish name | — | 2 (`infusión sin azúcar`, CIQUAL id 2669) |
| Food matches lost | — | 0 |
| Measures chosen / released | — | 0 / 0 (no ingredient has a measure) |

**The re-read changes almost nothing on this database, because almost nothing has a `raw_text`.** The
"Dieta 1" weeks (diets 4, 7, 9, 12) were stored before `V5` kept the cell text, so their recipes carry
none, and the story's own rule leaves a recipe without text untouched (the text is never rebuilt from
the parts). The ~60 distinct stored names that still carry a quantity (`1 cdta AOVE` ×90 rows, `1 kiwi`
×16, `1 melocotón` ×16, `2 huevos` ×10, …) are in recipes without `raw_text` and were not touched. The
gap the story describes (≈105 matched vs 144 on re-import) is therefore still open; see "What is left".

## Per diet

kcal is the week total over the counted ingredients only (a partial total, as always).
`counted / unmatched / unmeasured` is the week summary over ingredient servings. "Matched" counts the
diet's ingredient rows pointing at a food (composition or branded).

| Diet | Patient | Name | Status | Rows | Matched before | Matched after | kcal before | kcal after | c / u / m before | c / u / m after |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 4 | Victor | Dieta 1 | ARCHIVED | 210 | 105 | 105 | 4588.42 | 4588.42 | 89 / 105 / 16 | 89 / 105 / 16 |
| 5 | Victor | Semana de prueba (revisada) | ARCHIVED | 2 | 2 | 2 | 365.40 | 365.40 | 2 / 0 / 0 | 2 / 0 / 0 |
| 6 | Victor | Postoperatoria | ARCHIVED | 110 | 28 | 28 | 1153.72 | 1153.72 | 27 / 82 / 1 | 27 / 82 / 1 |
| 7 | Victor | Dieta 1 | ARCHIVED | 210 | 105 | 105 | 4600.18 | 4600.18 | 90 / 105 / 15 | 90 / 105 / 15 |
| 8 | Victor | Semana de prueba | ARCHIVED | 4 | 2 | 2 | 30.96 | 30.96 | 2 / 2 / 0 | 2 / 2 / 0 |
| 9 | Victor | Dieta 1 | **ACTIVE** | 211 | 105 | **106** | 4600.18 | 4600.18 | 90 / 106 / 15 | 90 / 105 / 16 |
| 12 | Prueba 1 | Dieta 1 | ARCHIVED | 211 | 105 | **106** | 4600.18 | 4600.18 | 90 / 106 / 15 | 90 / 105 / 16 |
| 13 | Prueba 1 | Dieta de Prueba 2 | **ACTIVE** | 1 | 1 | 1 | — | — | 0 / 0 / 1 | 0 / 0 / 1 |

## The recipes that were re-read

| Recipe | Diet | `raw_text` | Change |
| --- | --- | --- | --- |
| 229 | 9 | `2 tostadas integrales (2) + 1 aguacate (80 gr)  + salmón ahumado (50 gr) + 1 infusión sin azúcar + 1 kiwi (100 gr)` | row 934 `1 infusión sin azúcar` → `infusión sin azúcar`, matched to CIQUAL 2669 |
| 266 | 12 | the same text (diet 12 is a copy of 9) | row 1481, the same change |
| 519 | 13 | `2 huevos` | none (already read as `huevos`, 2 `unidad`) |

The new match is `1 unidad` of an infusion: matched now, but written in a unit nothing weighs, so it
moved from `unmatched` to `unmeasured` and the kcal did not change. The other bracketed fragments of
recipe 229 (`1 aguacate (80 gr)`, `1 kiwi (100 gr)`, `2 tostadas integrales (2)`) read the same as
before: today's parser reads a fragment with brackets exactly as it always did, count in the name
included.

## Recipes left untouched

- **Because the re-read gives a different number of ingredients:** none.
- **Because they have no `raw_text`:** 298 recipes — every recipe of diets 4, 5, 6, 7 and 8, and every
  recipe of diets 9 and 12 except 229 and 266.

## What is left (for the PO)

1. **Most stored names with a quantity are in recipes without `raw_text`.** Reading each stored
   `raw_name` on its own as one fragment (`1 cdta AOVE` → `AOVE`, 1, `cdta`) would reach them, but it
   is a different rule from this story's (the name is not the text as written, and for a name whose
   quantity came from a bracket, `1 kiwi` with 100 gr, only the name may be taken). It needs a decision.
2. **A bracketed fragment with a leading count keeps the count in its name** (`1 aguacate (80 gr)`,
   `1 kiwi (100 gr)`), on a fresh import too, so exact matching misses them there as well.
