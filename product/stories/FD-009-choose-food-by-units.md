# FD-009 Choose a food by units (2 huevos), not by grams

Status: done 2026-10-02 · Size: M · Created: 2026-10-02
For: nutritionist (writes it) · patient (reads it)

## Problem
As a nutritionist building a week, I want to add "2 huevos" or "1 rebanada de pan" to a plate
by counting units, so that the patient gets an instruction they can follow in a kitchen and I do
not have to convert every piece into grams.

Today the composer ("Añadir por raciones") weighs only measures with one published weight.
AESAN publishes `1 huevo mediano = 53–63 g`, a range, so the egg button is disabled and the only
way is to write grams. Foods with no published unit (pan de molde, jamón en lonchas, galletas)
have no unit choice at all. The per-diet criterion (`PUT /api/diets/{id}/measures`) exists, but
it is hidden, it must be repeated for each diet, and it cannot be used before the diet is saved
or in a library recipe.

## Decisions (user, 2026-10-02)
- A range is never weighed silently. The first time a unit is used, the nutritionist confirms a
  weight per unit (prefilled with the midpoint, shown as a proposal). It is then remembered.
- That weight is the **nutritionist's global criterion**: reused in every diet of every patient,
  and in library recipes. Labelled as hers, never as AESAN's.
- The nutritionist can create a unit a source does not publish (`1 rebanada = 30 g` for a bread),
  with a measure word from the existing vocabulary.
- The patient reads units only: `2 unidades medianas`. Grams stay on the nutritionist's side.

## Acceptance criteria
Builder (nutritionist):
- [x] In "Añadir por raciones", search `huevo`, pick `Huevo, entero, crudo` (or any egg food). The
      `1 huevo mediano (53–63 g)` choice is selectable, not disabled.
- [x] Selecting it with no criterion saved asks "peso por unidad", prefilled 58 g and marked as a
      proposal; the source range (53–63 g, AESAN 2022) is shown beside it.
- [x] Confirming saves the criterion. Picking egg again in **another patient's diet** offers
      `1 huevo mediano · 58 g · tu criterio` directly, with no question.
- [x] Count `2` + Añadir writes a fragment in units: `… (2 unidades medianas)`, not grams. The
      composer shows the computed weight (116 g) to the nutritionist.
- [x] The day/week kcal and ration count include the eggs (counted, not `unmeasured`), and the
      count says the weight rests on a measure (`countedByMeasure`).
- [x] For a food with no published unit (e.g. a pan de molde), the nutritionist can create a unit
      (`rebanada`, 30 g) from the composer; it is saved as her global criterion and used at once.
- [x] Works in a new, not yet saved diet and in a library recipe.
- [x] (list per food only; full screen → FD-017) Global criteria can be listed, changed and deleted. Deleting one still weighing an
      ingredient is refused with a clear message. Changing one says how many ingredients it
      affects before saving (live in every diet, like a library recipe).
- [x] A diet's own criterion for the same food and measure still wins over the global one.
- [x] A reference re-sync (`POST /api/reference/sync` or startup) never removes or changes a
      global criterion.

Patient:
- [x] The patient screen shows `Huevo… 2 unidades medianas` with no grams; with servings 1.5 it
      shows 3.

## Tasks
- backend (migration — restart the backend on port 5000 after; `gradlew test` migrates the real
  `.env` DB):
  - Store a global nutritionist measure criterion (one food + measure word + size + grams/ml per
    unit, note), not tied to a diet and not touched by the reference sync.
  - CRUD endpoints for it; delete refused while in use; usage count for an edit.
  - Measure listing for a food (`GET /api/reference/measures`) returns it, labelled as the
    nutritionist's criterion.
  - Measure choice precedence: picked > diet criterion > global criterion > published.
  - Allowed on library recipe ingredients (it belongs to no diet).
  - `compose` with a measure writes the fragment in units and the ingredient is weighed and counted.
  - Tests for precedence, re-sync safety, delete-in-use, library recipe.
  - Update CLAUDE.md (endpoints, migration table, reference section).
- frontend:
  - RationComposer: range measure selectable → confirm weight per unit (prefilled midpoint,
    labelled proposal, source range shown) → saves global criterion → adds in units.
  - "Nueva unidad" in composer for foods without one (measure word from `GET /api/reference/vocabulary`).
  - Show the nutritionist the grams the units weigh; patient views stay units-only.
  - A place to list/edit/delete global criteria (minimal, can live in the composer or a settings menu).
  - `pnpm build` clean.
- tech-lead: review backend and frontend diffs after both are done.

## Open questions
- None blocking. Defaults taken (say if wrong):
  - The patient reads the BEDCA name (`Huevo, entero, crudo`). A friendlier name is FD-010.
  - Measure words are the closed vocabulary in code; a new word (e.g. `galleta`) is a code
    change, and `unidad` covers it meanwhile.

## Hand-off prompts
### backend
Implement product/stories/FD-009-choose-food-by-units.md, backend half. Read the story first,
including the Decisions section. Summary: the nutritionist needs a **global measure criterion**
— her own weight per unit for one BEDCA food and one household measure (+ optional size),
e.g. `huevo mediano = 58 g`, `rebanada de pan de molde = 30 g` — reused by every diet, every
patient and library recipes, labelled as hers and never as published data, and never removed or
changed by the reference sync. Extend the existing `ref_food_measures` / diet-criterion design
rather than reshaping it (today `diet_id` marks a diet's own criterion). Provide CRUD endpoints
(delete refused while an ingredient or extra uses it; an endpoint or field giving how many
ingredients a change would affect). `GET /api/reference/measures?bedcaFoodId=` must return the
global criterion so the composer can offer it; measure choice precedence is picked > diet
criterion > global criterion > published. Library recipe ingredients may use a global criterion
(it belongs to no diet). `POST /api/diets/compose` with that measure must write the fragment in
units (`… (2 unidades medianas)`) and the ingredient must be counted in nutrition and rations
(`countedByMeasure`). Needs a Flyway migration. Add tests for precedence, re-sync safety,
delete-in-use and the library-recipe case; update CLAUDE.md. Report: endpoints and DTO shapes
(exact JSON), migration name, and anything you found that looks like a bug (do not fix unrelated
bugs; list them).

### frontend
Implement product/stories/FD-009-choose-food-by-units.md, frontend half, against the endpoints
the backend reported: <paste backend report>. In `RationComposer.vue`: a range measure (egg,
53–63 g) is selectable; with no criterion it asks the weight per unit, prefilled with the
midpoint and labelled as a proposal with the published range and source beside it; confirming
saves the global criterion and the food is added in units through `compose`. A food with no unit
gets "Nueva unidad" (measure word from `GET /api/reference/vocabulary`, size optional, grams). Show
the nutritionist the grams the units weigh; patient views stay units-only (check `RecipeDetail`).
Add a minimal place to list/edit/delete global criteria, showing the affected-ingredient count
before an edit. Spanish UI, bespoke CSS. `pnpm build` clean. Report anything that looks like a bug.

## Notes
- 2026-10-02 — Published unit measures today: 5 al día fruits/veg (fixed), AESAN yogur 125 g,
  egg 53–63 g (range), AESAN/MEC legumes spoon. Bread, embutido, galletas: none.
- 2026-10-02 — Backend done. V14 adds `ref_food_measures.global_criterion` (+ `uk_ref_food_measures_criterion`, one per food+measure+size). New `/api/reference/criteria` CRUD + `/{id}/usage`; `FoodMeasureDto.globalOwn`. Precedence picked > diet > global > published. 239 tests green; HTTP not exercised live.
- 2026-10-02 — Backend notes: a criterion is NET_EDIBLE (edible part) — UI labels it so; AESAN 53–63 g may be whole egg, so 58 g may overstate the edible part ~10% (question to user, non-blocking). One criterion = one BEDCA food (`Huevo, entero, crudo` ≠ `Huevo, cocido`).
- 2026-10-02 — Bugs found → FD-011 (diet criterion delete unweighs silently), FD-012 (compose 400 vs 404), FD-013 (oversized services).
- 2026-10-02 — Frontend done: new `MeasureCriterionForm`, `MeasureCriteriaList`, `ComposerChoices`, `BedcaFoodSearch`, `RecipeIngredientsField`; library dialog now has the composer. Build clean, not verified live.
- 2026-10-02 — Unmet: patient wording `2 unidades medianas` (shows `2 unidades (tamaño mediano)`); regression: `kiwi mediano · 1 unidad (tamaño mediano)`; servings scale number not unit (`2 unidad`). Backend follow-up: unit words with size agreeing, singular + plural, derived on read. Then frontend uses them, then tech-lead.
- 2026-10-02 — Backend follow-up: `DishIngredient.unitWording` / `ExtraFoodDto.unitWording` (`UnitWording.of`, derived on read). UI picks singular when quantity × servings = 1. Frontend follow-up started.
- 2026-10-02 — Frontend follow-up: `unitFor` / `amountText` in `dishText.ts`; patient reads `2 unidades medianas`, ×1.5 `3 unidades medianas`, kiwi `kiwi mediano · 1 unidad`. Extra bugs folded into FD-014 (extras "cada una") and FD-015 (raw unit in fix-up form). Tech-lead review started.
- 2026-10-02 — Tech-lead review: backend sound (precedence, re-sync, query counts); fixed composer race (food changed while measures loading → wrong measure id), Spanish messages for refused delete/duplicate. Builds green. AC 8 partly met → FD-017. Other leftovers → FD-018, FD-019. Remaining: live check after backend restart, then done.
- 2026-10-02 — Live check by user FAILED: "buscar alimento por raciones" shows nothing; with only a count chosen, g per portion stays empty so nothing can be added. Backend on 5000 is up with V14 and answers (`/api/bedca?name=huevo`, `/api/reference/measures`, `/api/reference/criteria` 200, also via the 5173 proxy) → UI bug. Egg foods are `Huevo de gallina, …` (id 2127 fresco), not `Huevo, entero, crudo`. Rework sent to frontend, verified in the browser this time.
- 2026-10-02 — Seen while checking: AESAN `1 huevo mediano` is offered for yolk (2125), white (1173) and dried yolk (762) by the keyword `huevo` → candidate bug, confirm after the rework.
- 2026-10-02 — Frontend rework, browser-verified. Causes: (1) `RationComposer` prefilled grams only for a fixed ration; a range ration (egg 53–63 g, bread 40–60 g…) left grams empty and Añadir disabled → now starts at the midpoint marked as a proposal (`composerChoice.rationStart`). (2) BEDCA search is a substring match: `huevos`, `pan de molde` find nothing, and a failed request looked like an empty result → UI falls back to the first word, distinct error message, hint on how BEDCA names are written (stopgap; real fix FD-020). Live a–g pass (egg 58 g criterion, `(2 unidades medianas)`, kcal counted, `countedByMeasure` 2, rebanada 30 g, grams path, patient units only). Test data removed. Not yet seen live: another patient's diet, library recipe, unsaved diet, refusal while in use, servings 1.5. Tech-lead review + those checks next.
- 2026-10-02 — Question to user: ranged *rations* now also start at the midpoint as a proposal (story decision was about units). Default kept; reverse if wrong.
- 2026-10-02 — Tech-lead review of rework + remaining live checks: all PASS in browser — cross-patient reuse (no question), library recipe, unsaved cell, delete refused while in use (Spanish msg), edit shows count before save, diet criterion beats global (API), patient ×1.5 `3 unidades medianas`. Fixes: search `pick` reset, empty first word, stale error in `refreshMeasures`, one `midpointProposal`. Build clean. "Semana en blanco" stores the diet at once, so "unsaved diet" = unpublished cell + library dialog (`dietId` null) — accepted as meeting the AC unless user objects. Leftover test data: patient "Prueba FD-009 temporal" (id 4) + empty diet 14 (no diet delete exists → FD-023). Status: verified live; waiting on the user's own try to close.
- 2026-10-02 — Closed by the user. Defaults accepted with the close: ranged rations start at the midpoint as a proposal; "unsaved diet" = unpublished cell + library dialog. AC 8 full-list screen → FD-017. Test leftovers (patient 4, diet 14) stay on the board.
