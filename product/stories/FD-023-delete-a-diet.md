# FD-023 Delete a diet

Status: done · Size: M · Created: 2026-10-02
For: nutritionist

## Problem
As a nutritionist I want to delete a diet — a test, a bad import, a mistake — so that a patient's
list holds only real weeks and a patient with no real diets can be removed. Today no diet can be
deleted, and the patient delete refusal says "Delete the diets first" with no way to do it.

## Decisions (2026-10-02, see decisions.md)
- Any diet may be deleted, active or archived, after a confirm that states what goes with it.
- Deleting the active diet leaves the patient with **no diet in force**; no archived diet is
  reactivated.
- Patient delete stays **refused** while diets exist (no cascade); the message points at the new
  delete.

## Acceptance criteria
- [x] `DELETE /api/diets/{id}` → 204; unknown id → 404.
- [x] The diet's days, dishes, scores, extras and own measure criteria are gone (schema cascades).
- [x] Its **private** recipes are deleted; **library** recipes it served stay, untouched, still
      served by other plates.
- [x] Global criteria are untouched.
- [x] Deleting the active diet: `GET /api/diets/active?patientId=` → 404 afterwards; the patient
      disappears from `GET /api/diets/current`; archived diets stay archived.
- [x] The confirm shows the diet name and the counts that go with it (scored plates, extras), so
      the nutritionist knows a journal is lost. Counts come from the API, not computed in the UI.
- [x] UI: the nutritionist can see a patient's diets — active and archived — and delete any of
      them from there, with that confirm. Spanish copy.
- [x] After deleting the active diet, the builder shows the patient as having no diet (same
      state as a new patient), no error.
- [x] Patient delete refusal (400) message, in Spanish in the UI, tells where to delete the diets.
- [x] Live: delete diet 14, then patient "Prueba FD-009 temporal" (id 4) — both gone.
- [x] CLAUDE.md updated (endpoint, private-recipe cleanup, patient refusal wording).
- [x] Tests green; `pnpm build` clean.

## Tasks
- backend: `DELETE /api/diets/{id}` in `DietService`; delete private recipes through
  `RecipeService.deletePrivate`; counts for the confirm (journal side through `JournalService`, or
  a `GET /api/diets/{id}/footprint`-style answer — agent's choice); patient refusal message;
  tests; CLAUDE.md. **No migration expected** (FKs already `ON DELETE CASCADE`).
- frontend: diets list per patient (active + archived via `dietsApi.history`, today unused);
  delete with confirm and counts; builder empty state after deleting the active one; refusal text.
- tech-lead: review both.

## Open questions
- None blocking. Default proposed: the diets list sits beside the patient in `PatientSelect`
  (where patient delete already lives); frontend may place it better and report.

## Hand-off prompts
### backend
Implement FD-023 (read `product/stories/FD-023-delete-a-diet.md`). Add `DELETE /api/diets/{id}`
(204, 404 unknown). The schema already cascades meals, dishes, scores, extras and the diet's own
measure criteria; you must delete the diet's **private** recipes (`RecipeService.deletePrivate`) and
leave library recipes and global criteria alone. Deleting the active diet leaves the patient with
no diet in force — never reactivate an archived one. Give the UI what it needs for a confirm: the
number of scored plates and extras the diet holds (choose the shape; one query each, no loops).
Reword the patient-delete refusal in `PatientService` (~L86) so it points at deleting diets one by
one. Tests, and update CLAUDE.md (endpoints, the delete's reach). No migration expected — say so if
you find one is needed. List, do not fix, any out-of-scope bug you see. Report the exact API added.

### frontend
Implement FD-023 UI (read `product/stories/FD-023-delete-a-diet.md`) against the API the backend
reported: <paste report>. Show a patient's diets — the one in force and the archived ones
(`dietsApi.history`) — with a delete per diet; confirm in Spanish with the diet name and the
counts of scored plates and extras that go with it. After deleting the active diet the builder
shows the patient with no diet, as for a new patient. Show the patient-delete refusal as written.
Default place: beside the patient in `PatientSelect`; move it if a better place exists and say why.
Bespoke CSS. `pnpm build` clean. List, do not fix, out-of-scope bugs.

## Notes
- 2026-10-02 — Refined with the user; started. Leftovers (patient id 4, diet 14) are the live check.
- 2026-10-02 — Backend done: `DELETE /api/diets/{id}` (204/404), `GET /api/journal/{dietId}/counts` → `{dietId, scored, extras}`; patient refusal reworded (English, like every backend refusal — UI shows its own Spanish text for it). Also moved `compose` into new `DietComposeService` to keep `DietService` from growing (unasked; tech-lead to review). `gradlew build` green, no migration. Live check not run yet.
- 2026-10-02 — Frontend done: "Dietas" button per patient in the builder's `PatientSelect` (not on `/mi-dieta`), list En vigor + Archivada with paging, in-page confirm with counts, Spanish refusal for patient delete. `pnpm build` clean. Live check on a fresh backend (port 5001): diet 14 deleted, then patient 4 deleted, API confirms; no console errors. Not seen live: archived row, counts > 0, paging, unpublished-cells warning, phone layout. User's backend on 5000 is an old build — restart needed. Tech-lead review started.
- 2026-10-02 — Tech-lead review done: fixed stale history answer overwriting another patient's list, "Ver más" skipping a diet after a delete, CLAUDE.md refusal line. Backend unchanged (delete order, cascades, transaction checked). Both builds green. All AC met; not seen live: archived row, counts > 0, paging, unpublished warning, phone. Proposed fix → FD-029. Waiting on the user's own try to close.
- 2026-10-02 — User's live try: delete fails with "journalApi.counts is not a function". Source has `counts`, backend answers `/api/journal/{id}/counts`; Vite on 5173 serves an old transform of `journal.ts` (file watcher missed the edit). Not a code bug — restart `pnpm dev` and retry.
- 2026-10-02 — User confirmed live after dev server restart. Done.
