# FD-052 A bracket state that contradicts the food is flagged, never totalled silently

Status: done (4c374f6) · Size: M · Created: 2026-10-03 · Refined: 2026-10-04 · Absorbs FD-001
For: nutritionist

## Problem
As a nutritionist I want `Lenteja, cocida (55 g en seco)` or `pechuga a la plancha (150 g en
crudo)` read with the bracket's state, so that a total never counts 55 g of dry lentils as cooked
(69 kcal instead of about 190). Today the parser drops the state when name and bracket disagree:
`state: null`, no `stateMismatch`, no yield hint. The composer writes exactly that text (crosswalk
has only `Lenteja, cocida`; the AESAN ration is dry).

## Decisions (2026-10-04)
- **The bracket wins for the quantity**: it says in which state the grams were weighed; the name
  still says which food it is. Proposed default, accepted ("yes to all"); closes FD-001's question.

## Acceptance criteria
- [x] `Lenteja, cocida (55 g en seco)` reads state = dry and, matched to `Lenteja, cocida`, carries
  `stateMismatch`.
- [x] `pechuga a la plancha (150 g en crudo)` reads state = raw; matched to a cooked chicken food it
  carries `stateMismatch` and the yield hint (about 108 g at 72 %).
- [x] A state mismatch with no published yield is **not** totalled (counted as not weighable, counts
  still summing to `ingredients`) — never totalled at the wrong state.
- [x] Nothing converts a quantity by a yield (yields stay offered only).
- [x] Where name and bracket agree, or only one states anything, behaviour is unchanged.
- [x] Composer: when the ration's state differs from the chosen food's state, it says so before
  adding (e.g. "La ración es en seco; el alimento elegido está cocido").
- [x] Parser tests for the cases above; `ExampleDietCompositionMatchTest` floor holds (144/210).
- [x] CLAUDE.md updated (parser and nutrition sections).

## Tasks
- backend: parser keeps the bracket state; nutrition does not total a mismatch without a yield;
  tests; CLAUDE.md. No migration expected.
- frontend: composer warning; show `stateMismatch` on chips if the DTO carries it.
- tech-lead: review.

## Hand-off prompts
### backend
Implement FD-052 (`product/stories/FD-052-bracket-state-contradicts-name.md`), backend half.
`MealTextParser` must keep the bracket's state when it contradicts the state in the name (the
bracket is the state the quantity was weighed in). A matched ingredient whose state differs from its
food's state must carry `stateMismatch` and, where a published yield fits, the `yieldHint`; when no
yield fits it must not be totalled, and the counts must still sum to `ingredients`. Never convert a
quantity by a yield. Tests; keep `ExampleDietCompositionMatchTest` at 144/210 or better; update
CLAUDE.md. Report the API shape the composer can use to warn about a state difference. List, do not
fix, any bug outside this story.

### frontend
Implement FD-052 frontend half against the backend's report: in `RationComposer.vue`, warn before
adding when the ration's state differs from the chosen food's state; show `stateMismatch` where the
ingredient chips show matches, if the DTO carries it. `pnpm build`, check live on 5173. List, do not
fix, any bug outside this story.

## Notes
- 2026-10-04 backend: `FoodState.ofWriting` bracket wins on disagreement; `RecipeIngredient.isStateMismatch()`; mismatch never totalled (yield or not), `NutritionSummaryDto.unmeasuredByState`. 343 tests, 144/210. No migration; 5000 restart. Follow-ups FD-060, FD-061.
- 2026-10-04 frontend: composer warning via `useComposePreview` (debounced compose); `uncountedReasons`; live-checked with test diet (deleted). Follow-ups FD-062, FD-063.
