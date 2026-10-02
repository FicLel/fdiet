# Product decisions

Append-only. Newest at the bottom. A reversed decision gets a new entry naming the one it replaces.

## Before this file existed (recorded 2026-10-02 from CLAUDE.md, plan.md and earlier sessions)

- **2026-08-30** — Bespoke CSS, no component library; the UI talks to the live API.
- **2026-08-30** — The patient's side (scores, off-plan food) gets a real backend: the `journal` context.
- **2026-08-31** — Multiple patients, **no authentication**; every patient is visible to anyone. A patient is a name.
- **2026-09-13** — fdiet stays open source forever; BEDCA data stays non-commercial regardless. (plan.md Q1)
- **2026-09-13** — AESAN 2022 is the default adult reference profile. (Q2)
- **2026-09-13** — A nutritionist may override a measure's weight for one diet, labelled as their criterion. (Q3)
- **2026-09-13** — Adult meal energy split is borrowed from the AESAN/MEC 2010 school document, with a label. (Q4)
- **2026-09-13** — Clinical exchanges (diabetes 10 g HC) sit behind a `clinical` flag on the diet. (Q5)
- **2026-09-13** — Patients may carry birth date and sex. (Q6)
- **2026-09-28** — A dish is a description plus a recipe; library recipes are linked and shared by all patients, edits are live; imported cells become private recipes; per-patient portions go through `servings`.
