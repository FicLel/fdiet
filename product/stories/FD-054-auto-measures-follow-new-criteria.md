# FD-054 An auto-chosen measure follows a new criterion; a picked one stays

Status: done (backend 4c374f6; frontend uncommitted, 2026-10-04) · Size: M · Created: 2026-10-04 · Refined: 2026-10-04 · Absorbs FD-018
For: nutritionist

## Problem
As a nutritionist I want a unit weight I add later (diet or global criterion) to weigh the
ingredients the machine weighed on its own, in weeks already written too, so that my criterion
reaches every week without republishing. A measure I picked by hand must stay.

Today nothing records whether a measure was picked or chosen by the rule. Since FD-048 every
measure in a re-parsed cell goes back as a pick, and a new global criterion does not reach stored
ingredients (FD-018).

## Decisions (2026-10-04)
- **Yes**: a new or changed criterion replaces auto-chosen measures in written weeks, archived
  included; a picked measure is never replaced. (User, "yes to all".)

## Acceptance criteria
- [x] Each ingredient and extra records whether its measure was **picked** by a person or chosen by
  the rule; the DTOs carry it.
- [x] A measure set by a person (fix-up, composer measure choice, PATCH `foodMeasureId`) is picked.
- [x] Parse `keep` and `PUT` keep a picked measure (FD-039 re-validation still applies); an
  auto-chosen one is chosen again by the rule.
- [x] Creating or changing a global or diet criterion re-chooses the measure of every
  ingredient/extra it now applies to whose measure is not picked; the answer says how many.
- [x] Migration loses no measure a person may have picked (default: a stored measure counts as
  picked unless the rule would choose the same row today — backend to confirm).
- [x] Composer and fix-up label a measure as the person's choice or automatic.
- [x] Tests; CLAUDE.md. **Needs a migration**: restart backend on 5000; `gradlew test` migrates the
  real `.env` DB.

## Tasks
- backend: picked flag (migration), keep/PUT rule, re-choose on criterion change, tests, CLAUDE.md.
- frontend: `keep` sends a measure only when picked; person choices marked picked; label.
- tech-lead: review.

## Hand-off prompts
### backend
Implement FD-054 (`product/stories/FD-054-auto-measures-follow-new-criteria.md`), backend half.
Add a "picked" flag for the measure of recipe ingredients and extras (migration); set it only when a
person chose the measure; keep picked measures through parse `keep` and `PUT`; re-choose non-picked
measures when a global or diet criterion is created or changed (batched, no per-row queries; answer
the count). Migrate existing rows without losing a possible human pick. Tests, CLAUDE.md. Tell the
user the backend on 5000 needs a restart. List, do not fix, any bug outside this story.

### frontend
Implement FD-054 frontend half against the backend's report: send `foodMeasureId` in parse `keep`
only for picked measures; mark person choices as picked; label picked vs automatic in the composer
and fix-up panel. `pnpm build`, live check. List, do not fix, other bugs.

## Notes
- 2026-10-04 backend: V19 `measure_picked` + V20 (Java) frees rule-chosen; `IMeasureReweigher` port (`RecipeMeasureReweigher`, `JournalService`); `DietMeasureService` split out. Criteria POST/PUT answer `{measure, reweighed}` (breaking); diet measures PUT `{measure, attached, reweighed}`. 368 tests. Not live-verified: criterion write, V20 on measured rows (dev DB has none). Follow-ups FD-065, FD-066, FD-067.
- 2026-10-04 frontend: agent stopped by usage limit mid-run; partial UI work committed in 4c374f6 by the user. Resume: finish frontend, `pnpm build`, live check, then tech-lead.
- 2026-10-04 frontend (resumed): build fixed (`IngredientAt.measurePicked`, criterion form answer); `keep` sends `foodMeasureId` only when picked; `reweighedText` message after diet/global criterion save ("cambia de medida" — backend counts rows whose measure changed, not new weights); composer preview says "elegida por ti / automática". `pnpm build` clean. Live: auto vs picked label, keep body, publish flags, diet criterion moved only the automatic oil, fix-up pick turns picked, global criterion from composer; test data deleted. Not live: global criterion edit from list, final message wording, patient view, mobile. Follow-ups FD-068, FD-069, FD-013 re-measured.
- 2026-10-04 tech-lead: backend no bugs (fixed query count, scope, transaction, flag consistent). UI fix: composer no longer shows re-weighed sentence twice while criterion panel open. 371 tests, `pnpm build` clean. Nits: `.done` style copied in 3 components; FD-070.
