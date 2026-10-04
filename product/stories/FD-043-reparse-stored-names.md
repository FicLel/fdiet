# FD-043 Re-read stored ingredient names with today's parser

Status: ready · Size: M · Created: 2026-10-03 · Refined: 2026-10-04
For: nutritionist

## Problem
As a nutritionist I want weeks stored by the older parser to match as well as a fresh import, so
that I do not fix by hand what the importer now reads alone. Stored names still carry quantities
(`1 cdta AOVE` ×22, `1 kiwi`, `2 huevos`), so exact matching misses them: "Dieta 1" stored ~105
matched vs 144 on re-import.

## Decisions (2026-10-04)
- **Yes**: re-parse stored `recipes.raw_text` with today's parser; this rewrites `raw_name`,
  quantity and unit. (User, "yes to all".)
- Defaults (proposed): runs once per database as a Java Flyway migration, like V18; all diets,
  archived included; no existing match or picked measure is lost.

## Acceptance criteria
- [ ] Every recipe with `raw_text` is re-read; ingredients get today's name, quantity, range, unit,
  state and size.
- [ ] An ingredient already matched keeps its food and measure (paired by position, FD-048 rule).
- [ ] Unmatched ingredients are re-matched by exact Spanish name/alias (V18's rule).
- [ ] A recipe whose re-read gives a different number of ingredients is left untouched and listed.
- [ ] Recipes without `raw_text` are untouched.
- [ ] Before/after per diet in `product/reports/FD-043-before-after.md` (matched count, kcal).
- [ ] Tests; CLAUDE.md migration table. **Needs a migration**: restart backend on 5000;
  `gradlew test` migrates the real `.env` DB.

## Tasks
- backend: Java migration, tests, report, CLAUDE.md. After FD-052 and FD-054 (parser, picked flag).
- tech-lead: review.

## Hand-off prompts
### backend
Implement FD-043 (`product/stories/FD-043-reparse-stored-names.md`); FD-052 and FD-054 are already
in. A Java Flyway migration that re-reads every recipe's `raw_text` with today's `MealTextParser`,
rewrites name/quantity/range/unit/state/size, keeps every existing food match and picked measure,
and re-matches the unmatched by exact Spanish name (V18's rule). Leave a recipe untouched if the
ingredient count changes, and list it. Write `product/reports/FD-043-before-after.md`. Tests,
CLAUDE.md. Tell the user the backend on 5000 needs a restart. List, do not fix, any bug outside this
story.
