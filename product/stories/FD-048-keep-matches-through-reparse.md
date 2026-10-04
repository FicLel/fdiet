# FD-048 Keep food matches through a re-parse

Status: review done, uncommitted · Size: M · Created: 2026-10-03 · Refined: 2026-10-04
For: nutritionist

## Problem
As a nutritionist I want a food I matched (in the ration composer or in the fix-up list) to stay
matched when I edit the cell's text, so that my choice is never undone silently on publish.

Today the editor re-reads the whole cell through `POST /api/diets/parse` after every text change
(`UI/src/stores/dietDraft.ts` `setRecipeText` → `reparse`) and replaces the draft recipe with the
answer. Parse matches by exact Spanish name only, so:
- a composer food with no Spanish name (most of the ~10k CIQUAL/BLS foods) comes back **unmatched**;
- a composer food that is not the name's preferred row comes back as the **preferred** food, silently;
- a match made by hand in the fix-up list (PATCH) is lost the next time any text in that cell is
  edited and published.

`RationComposer.vue` `add()` gets the pinned ingredient back from `compose` but emits only the
fragment text, so the pin is thrown away at once.

## Decisions (2026-10-04, see decisions.md)
- Option **b**: the draft keeps the matches through the re-parse. The composer keeps offering every
  CIQUAL/BLS food (option a, narrowing it to ~123 crosswalked foods, rejected).
- Scope: **every** existing match, composer pins and fix-up matches alike, under one rule.

## Rule
- An ingredient whose **name is unchanged** after the re-parse keeps the food it was matched to
  before (`compositionFoodId` or `foodItemId`), even when parse would match it to another food or to
  none. The person's choice beats the exact-name rule.
- Its picked measure (`foodMeasureId`) is kept too while the unit is unchanged. The backend already
  re-validates a picked measure on save (FD-039).
- An ingredient whose **name was edited** is a new ingredient: parse decides, nothing carried.
- Same name twice in one cell: matched in order of appearance.
- Name comparison: same as parse's own (case and accents ignored) — agents to confirm.

## Acceptance criteria
- [x] Composer: add a food with **no Spanish name** by rations, publish → the stored ingredient
      points at that food (`compositionFoodId` = the one chosen) and is counted in the totals.
- [~] Composer: add a **non-preferred** food whose Spanish name another row wins, publish → the
      stored ingredient keeps the chosen food, not the preferred one.
- [x] Fix-up: match an ingredient by hand, then edit another ingredient's text in the same cell and
      publish → the hand match is still there.
- [x] Editing a matched ingredient's **name** drops its match (parse decides again); editing only
      its quantity keeps the food.
- [x] Before publish, the cell's draft totals already use the kept food's figures, not the
      re-parsed one's (no wrong number on screen until publish).
- [x] The editor still has no parser of its own: reading text stays the backend's job.
- [x] Tests: backend covers kept matches through parse (if parse changes); `pnpm build` clean.
- [x] Live check on 5000/5173: the three scenarios above on a test diet, test data cleaned after.
- [x] CLAUDE.md updated if `POST /api/diets/parse` changes.

## Tasks
- backend (likely small, agent's call): let `POST /api/diets/parse` take the matches to keep —
  e.g. optional `keep: [{name, compositionFoodId | foodItemId, foodMeasureId?}]` — apply them by the
  rule above, and price the recipe with them, so the draft totals are right. No migration.
- frontend: `RationComposer` emits the pinned ingredient with the fragment; `dietDraft` holds the
  cell's matches (stored ingredients + composer pins), sends them on every `reparse`, and drops a
  match once its name no longer appears.
- tech-lead: review after both.

No migration. No restart needed unless the backend changes (then restart 5000).

## Hand-off prompts

### backend
> FD-048 (product/stories/FD-048-keep-matches-through-reparse.md). The editor re-reads a cell
> through `POST /api/diets/parse` after each text change and loses every match that is not an exact
> Spanish name: composer foods with no Spanish name, non-preferred rows, and hand matches from the
> fix-up list. Add an optional list of matches to keep to the parse request (shape is your call;
> suggested `keep: [{name, compositionFoodId | foodItemId, foodMeasureId?}]`). For each parsed
> ingredient whose name equals a kept one (the same comparison parse already uses; repeated names in
> order), use the kept food instead of the resolver's answer, and the kept measure while the unit is
> unchanged (re-validated as FD-039 does). Price the recipe with the kept foods. Keep both ids
> mutually exclusive. No migration. Tests for: food without Spanish name kept, non-preferred food
> kept over the preferred one, edited name not kept, measure dropped when the unit changes. Update
> the parse entry in CLAUDE.md. Report the final request shape for the frontend.

### frontend
> FD-048 (product/stories/FD-048-keep-matches-through-reparse.md). After backend lands the parse
> change (shape in its report): `RationComposer.vue` `add()` must emit the pinned ingredient that
> `compose` returns along with the fragment. In `dietDraft.ts`, keep per cell the matches to carry —
> the stored recipe's matched ingredients when the edit opens, plus composer pins — and send them on
> every `reparse` (and `settleParses`). Drop a carried match once no parsed ingredient has its name.
> The editor must not read text itself. Check live on 5173: composer food without Spanish name,
> non-preferred composer food, hand match surviving an edit of another ingredient — each still
> matched after publish, with draft totals right before publish. `pnpm build` clean. Delete test data.

### tech-lead
> Review FD-048 (backend parse `keep` + frontend draft pins) against the story's rule and acceptance
> criteria: layering, one name comparison shared with the resolver (no second copy), no per-ingredient
> queries, no parser in the UI.

## Notes
- Found 2026-10-03 in the FD-033 D review; deferred past the D commit (decision 2026-10-04).
- Related: FD-052 (composer writes `Lenteja, cocida (55 g en seco)`), separate story.

## Outcome (2026-10-04)
- Backend: `parse` takes `keep` (`KeptMatchDto`, `KeptMatches` on `Texts.key`); 349 tests green; CLAUDE.md updated.
- Frontend: per-cell `keep` in `dietDraft.ts`, composer emits its pin; `pnpm build` clean.
- Tech-lead: fixed stale `keep` copy (a PATCH/refresh after the edit opened could undo a hand match); not reproduced live.
- Live: no-Spanish-name composer food, hand match through another edit, renamed name drops / quantity edit keeps, measure kept and dropped on unit → g, library detach carries matches — all held after publish.
- `[~]` non-preferred food: not reproducible (every crosswalk row is preferred today); covered by `RecipeServiceKeepTest`.
- Left over → FD-053 (library dialog), FD-054 (auto measure becomes a pick), FD-055 (fix-up rename leaves `raw_text`), FD-056 (half-typed name drops a pin), FD-057 (detached copy keeps library row ids).
