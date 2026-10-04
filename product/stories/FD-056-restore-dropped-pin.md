# FD-056 A pin dropped by a half-typed name comes back when the name does

Status: done (uncommitted, 2026-10-04) · Size: S · Created: 2026-10-04 · Refined: 2026-10-04
For: nutritionist

## Problem
As a nutritionist I want a composer pin (food with no Spanish name) to survive me typing in the
middle of its name, so that typing the name back brings my food back. Today the debounced parse
reads the half-typed name, the pin is dropped, and retyping cannot restore it.

## Decisions (2026-10-04)
- **Restore**, not accept: the draft remembers a dropped pin until publish. (User, "yes to all".)

## Acceptance criteria
- [x] Add a food without Spanish name by rations; half-edit its name in the cell, then type it back
  exactly → matched to the same food again, with its measure.
- [x] A name changed for good (not restored before publish) is not matched to the old food.
- [x] After publish (or discarding the draft), remembered pins are forgotten.
- [x] Same for hand (fix-up) matches in the draft.
- [x] Frontend only; `pnpm build` clean; live check.

## Tasks
- frontend: per-cell memory of dropped `keep` entries in `dietDraft`, re-sent while the cell is in draft.
- tech-lead: review.

## Hand-off prompts
### frontend
Implement FD-056 (`product/stories/FD-056-restore-dropped-pin.md`). In `UI/src/stores/dietDraft.ts`
(and `UI/src/domain/keptMatches.ts`), keep the `keep` entries a re-parse dropped, per cell, until
publish or discard, and send them again on the next parse so a name typed back restores its match
and measure. Do not change the backend. `pnpm build`, live check on 5173. List, do not fix, other
bugs.

## Notes
- 2026-10-04 frontend: `keptMatches.toSend` / `droppedFrom`; `dietDraft` holds dropped entries per cell (Map), forgotten on revert/discard/publish/load; `recipeRequest` moved to `domain/requestIngredient.ts` (store 948 → 943). Picked-only measure rule (FD-054) kept. `pnpm build` clean. Live via store calls (not DOM): composer pin name + unit restored, name changed for good stays unmatched, hand match restored, publish and discard forget. Not checked: typing in textarea/composer dialog, library-recipe hand match. Follow-up FD-071.
- 2026-10-04 tech-lead: no bugs, no changes; `pnpm build` clean. Remember/forget points and FD-054 rule confirmed. Edge case added to FD-071.
