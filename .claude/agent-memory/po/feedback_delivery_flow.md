---
name: delivery-flow
description: How the user wants a story delivered — ask questions up front, then run backend → frontend → tech-lead; bugs found become new stories
metadata:
  type: feedback
---

When the user hands over a feature, they want me to ask every question that changes the story
(they said "ask as many questions as needed"), then launch the agents myself: backend first,
frontend against its reported API, tech-lead review last. Any bug found along the way becomes a
new FD story, never a silent fix inside the current one.

**Why:** stated by the user on 2026-10-02 when starting FD-009.
**How to apply:** batch questions with AskUserQuestion and recommended defaults; hand-off prompts
ask agents to list (not fix) out-of-scope bugs; open a story per bug in product/.
