# FD-041 Credit CIQUAL 2025 and BLS 4.0 in the footer

Status: done (4c374f6) · Size: S · Created: 2026-10-03 · Refined: 2026-10-04
For: both

## Problem
As the project owner I want the builder and patient footers to credit CIQUAL 2025 and BLS 4.0,
so that fdiet meets CC BY 4.0 wherever their figures show. Since FD-033 D (2026-10-03) every total
is computed from CIQUAL/BLS, but `UI/src/components/AttributionFooter.vue` credits only BEDCA.

## Decisions (2026-10-04)
- Own pass now, not inside FD-033 E: the licence gap is live today.
- The BEDCA line goes: no BEDCA figure is shown on any screen since FD-033 D.

## Acceptance criteria
- [x] Builder and patient (`/mi-dieta`) footers show the CIQUAL 2025 (ANSES) and BLS 4.0
  (Max Rubner-Institut) attribution, each with "CC BY 4.0" and a link to the source.
- [x] The wording matches the attribution strings the backend holds (`CompositionSource`); no
  invented text.
- [x] The BEDCA line is gone from both footers.
- [x] Reference-source lines ("Raciones y medidas: n fuentes") work as before.
- [x] Screens showing a food's source (fix-up panel, composer, extras) do not regress.
- [x] `pnpm build` clean; checked live on 5173.

## Tasks
- frontend: footer lines for CIQUAL and BLS, BEDCA line removed. A constant copied from
  `CompositionSource` is acceptable if no endpoint gives the strings without a sync (report it).
- tech-lead: review.

## Hand-off prompts
### frontend
Implement FD-041 (`product/stories/FD-041-composition-attribution-footer.md`). In
`UI/src/components/AttributionFooter.vue`, replace the BEDCA line with the CIQUAL 2025 and BLS 4.0
attribution lines (CC BY 4.0, a link each), using exactly the strings in
`src/main/java/com/fdiet/food/model/CompositionSource.java`. Keep the reference-source toggle. Both
footers (builder and `/mi-dieta`) must show them. Do not change the backend. Check live on 5173,
run `pnpm build`. Report what changed; list (do not fix) any bug outside this story.

## Notes
- 2026-10-04 frontend: `SOURCE_ATTRIBUTIONS` in `UI/src/domain/compositionFood.ts` (copied, links ciqual.anses.fr / blsdb.de). Builder footer checked live; `/mi-dieta`, toggle and source labels not — backend down. Follow-ups FD-058, FD-059.
