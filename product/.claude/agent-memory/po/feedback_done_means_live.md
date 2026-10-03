---
name: done-means-live
description: A story is done only when the user can use it in the running UI; a green build is not acceptance
metadata:
  type: feedback
---

Done means the feature works in the running app (UI on 5173, backend on 5000), not that `pnpm build` / `gradlew build` pass.

**Why:** on 2026-10-02 FD-009 passed both builds and the tech-lead review, but the user found the composer search empty and grams per unit blank when they tried it: "to said the job is done I need those options working".
**How to apply:** every frontend hand-off must ask the agent to reproduce and walk the acceptance criteria in the browser (claude-in-chrome) and report pass/fail per step. Never report a story as built/ready from build output alone. See [[delivery-flow]].
