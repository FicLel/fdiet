# FD-020 Word-wise ranked food search

Status: done · Size: S · Created: 2026-10-02 (found in FD-009 rework)
For: nutritionist, patient

## Problem
As a nutritionist (and a patient logging an extra) I want the food search to find foods by the
words I type, best match first, so that `huevos`, `pan de molde` or `jamón york` list the right
BEDCA foods instead of nothing.

Today `GET /api/bedca?name=` is a substring match on the whole term: `pan de molde` misses
`Pan blanco, de molde, tostado`. The composer hides this with a first-word fallback (stopgap from
FD-009). `NameMatcher` already ranks words (accents, plurals, quantity words dropped) for the
fix-up suggestions — the search should use the same ranking.

## Acceptance criteria
- [ ] `huevos` lists `Huevo de gallina, …` foods.
- [ ] `pan de molde` lists `Pan blanco, de molde, tostado` and `Pan integral, de molde, tostado`
      above other `Pan…` rows.
- [ ] `jamón york` lists `Jamón …` foods, `Jamón cocido…` among them (BEDCA has no "york"; synonyms are FD-030).
- [ ] A food matching any word of the term shows; more words matched ranks higher; then the
      `suggest` score, then name. (Story first said "tie goes to the more specific name" — copied
      from a wrong javadoc; real `suggest` order kept, specificity is FD-031.)
- [ ] A partial word still finds by substring, ranked after whole-word hits (`lechu` → `Lechuga`),
      so the box does not blank mid-typing. Added by backend; accepted by default 2026-10-02.
- [ ] Case and accents ignored: `JAMON` = `jamón`.
- [ ] Empty term behaves as today (alphabetical page).
- [ ] A term no food shares a word with shows the existing "nothing found" message, not an error.
- [ ] Same ranked search in all three boxes: composer "Añadir por raciones", fix-up food link
      panel (BEDCA half), patient extra-food panel (BEDCA half).
- [ ] Composer's first-word fallback and its "Ninguno se llama «…». Con «…»" hint are gone.
- [ ] Branded catalogue search (`/api/food`) unchanged.
- [ ] No migration. Backend tests green, `pnpm build` clean.

## Tasks
- backend: ranked word-wise BEDCA search through `NameMatcher` (reuse `suggest` index, no
  per-request scan beyond the in-memory 957 rows); endpoint shape is backend's call; tests for the
  three example terms; CLAUDE.md endpoint line.
- frontend: point the three BEDCA search boxes at it; remove the first-word fallback.
- tech-lead: review.

## Open questions
None blocking. Decided 2026-10-02: all three boxes; any word, ranked; synonyms later (FD-030).

## Hand-off prompts
### backend
Story FD-020 (read `product/stories/FD-020-ranked-food-search.md`). Make BEDCA food search
word-wise and ranked: a term's words are reduced by `NameMatcher` (accents, plurals, quantity
words) and foods are ranked by the same score and tie-break as `IBedcaFoodService.suggest`; any
food sharing at least one word shows. Empty term keeps today's alphabetical page. Choose the
endpoint shape (change `GET /api/bedca?name=` or add one) and report it exactly, with the response
shape, so frontend can call it. Reuse the in-memory suggestion index; no query per word, no
nested scans. Tests: `huevos`, `pan de molde`, `jamón york` per the story's acceptance criteria.
No migration expected — say so if one is needed. Update CLAUDE.md's endpoint list. Do not touch
UI/. List (do not fix) any out-of-scope bug you find.

### frontend
Story FD-020 (read `product/stories/FD-020-ranked-food-search.md`). Backend API: <paste backend
report>. Point the three BEDCA search boxes at the ranked search: `BedcaFoodSearch.vue`
(composer), `stores/foodLink.ts` (fix-up panel, BEDCA half), `ExtraFoodPanel.vue` (patient
extras, BEDCA half). Remove the first-word fallback and its hint from `BedcaFoodSearch.vue`; keep
the "nothing found" and error messages distinct. Branded search unchanged. `pnpm build` clean;
check the three example terms live if backend on 5000 is up. Do not touch src/. List (do not fix)
any out-of-scope bug you find.

## Notes
- 2026-10-02 — Refined with user; started.
- 2026-10-02 — Backend done: `GET /api/bedca?name=&page=&size=` kept, same `PageDto<BedcaFoodDto>`, `content` in rank order; blank name alphabetical; no hits = 200 empty page. Ranking from in-memory index, one `findAllById` per page. `findByNameContaining` removed. `BedcaFoodServiceSearchTest` 9/9, 263 tests green; full build / live not run. No migration. Found: FD-031 (suggest favours shorter names, javadoc wrong). Frontend started.
- 2026-10-02 — Frontend done: `BedcaFoodSearch.vue` fallback + hint removed, one call, "nothing found" copy no longer says "en singular"; `api/catalogue.ts` doc only; `foodLink.ts`, `ExtraFoodPanel.vue` unchanged (already in order, no client sort). `pnpm build` clean. Live not checked: backend 5000 old build. Found: FD-032, FD-013 widened. Tech-lead review started.
- 2026-10-02 — Tech-lead review: fixed short names (`Té`) missing from the index, composer stale results during debounce, ranked page no longer labelled name-sorted. `com.fdiet.food.*` 35 green (full suite not run), `pnpm build` clean. AC met in tests/code; live check of the three boxes pending a backend restart. Tail order (`té` buries `Té`) moved to FD-031.
- 2026-10-02 — User confirmed live: search works in the three boxes. Done.
