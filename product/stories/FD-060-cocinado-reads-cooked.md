# FD-060 "cocinado" is read as cooked

Status: done (uncommitted, 2026-10-04) · Size: S · Created: 2026-10-04 · Refined: 2026-10-04
For: nutritionist

## Problem
As a nutritionist I want the text the composer writes for a cooked ration (`… (150 g cocinado)`) to
read back as cooked, so that a cooked quantity against a raw or dry food is flagged and not totalled
at the wrong state. Today `DietComposeService.stateWords` and `DietRationService:333` write
"cocinado", but `FoodState` has no `cocinad[oa]s?` rule: the state reads back null, no
`stateMismatch`, and the grams are totalled as if the food's own state.

## Decisions (2026-10-04)
- User moved it ahead of FD-056 ("go ahead with FD-060").
- Default (proposed): fix the reader (add the word), not the writer — "cocinado" is what a person
  writes too.

## Acceptance criteria
- [x] `cocinado`, `cocinada`, `cocinados`, `cocinadas` read COOKED in a bracket and in a name
  (`FoodState.ofWriting` and `ofFoodName`).
- [x] Composer round trip: a COOKED ration composed onto a raw/dry food reads back COOKED and carries
  `stateMismatch` (not totalled, `unmeasuredByState` counts it).
- [x] `ofFoodName` effect checked over the crosswalk names: list any composition food whose state
  changes; none expected.
- [x] `ExampleDietCompositionMatchTest` holds at 144/210; existing state tests unchanged.
- [x] Tests; CLAUDE.md if the state words are listed there. No migration.

## Tasks
- backend: rule, tests, crosswalk check, CLAUDE.md.
- tech-lead: review.
- frontend: none.

## Notes
- Rows already stored with "cocinado" keep `state` null until re-read (publish, or FD-043).
- 2026-10-04 backend: `cocinad[oa]s?` in `FoodState` COOKED rule; `ComposedStateRoundTripTest` + vocabulary test. 370 tests, 144/210. Crosswalk: 0 names change. No migration; restart 5000. Not live-checked. Other composer state words read back correctly.
- 2026-10-04 tech-lead: no bugs. DRY: `FoodState.written()` now the one word table for composer + ration notes; test `everyWrittenStateReadsBackAsItself` fails the build if a writer word is unreadable. 371 tests. Pending: live check after 5000 restart.
- 2026-10-04 live (tech-lead): compose+parse `Pasta integral, seca (150 g cocinado)` and `Arroz integral, crudo (150 g cocinado)` → COOKED, `stateMismatch` true, unmeasuredByState 1, totals null. Crosswalk has no dry lentil (only `Lenteja, cocida`).

## Hand-off prompts
### backend
Implement FD-060 (`product/stories/FD-060-cocinado-reads-cooked.md`). `FoodState`
(`src/main/java/com/fdiet/reference/domain/FoodState.java`) has no rule for "cocinado/a/s", the word
`DietComposeService.stateWords` and `DietRationService` write for COOKED. Add it so both
`ofWriting` and `ofFoodName` read it COOKED. Test the composer round trip (COOKED ration onto a
raw/dry food reads back COOKED with `stateMismatch`, not totalled). Check every crosswalk Spanish name
for a state change and report any. Keep `ExampleDietCompositionMatchTest` at 144/210. Update CLAUDE.md
if needed. No migration expected; say if the backend on 5000 needs a restart. List, do not fix, any
bug outside this story.
