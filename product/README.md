# product/

Where fdiet's product work is tracked. Owned by the `po` agent (`.claude/agents/po.md`); anyone
may read it, and the developer agents read the story they are handed.

| File | What it holds |
| --- | --- |
| `board.md` | Now: in progress, next up, blocked, waiting on the user. |
| `backlog.md` | Every open story, highest priority first, with id, size and one line. |
| `stories/FD-###-slug.md` | One story refined: problem, acceptance criteria, tasks per agent, questions, hand-off prompts. |
| `decisions.md` | Product decisions, dated, with the reason. Append-only. |
| `log.md` | Dated one-line history: created, started, done, dropped. |

## Story lifecycle

`idea` → `refining` → `ready` → `in progress` → `done` (or `dropped`, with the reason).

- **Ready** means: acceptance criteria written, tasks split per agent, no blocking question.
- **Done** means: every acceptance criterion checked, tests green, `pnpm build` clean, CLAUDE.md
  updated by the developer agent when an endpoint or migration changed.

Sizes: **S** an afternoon, **M** a session or two, **L** split it.

## Story file template

```markdown
# FD-### Title

Status: refining | ready | in progress | done | dropped · Size: S/M/L · Created: YYYY-MM-DD
For: nutritionist | patient

## Problem
As a … I want … so that …

## Acceptance criteria
- [ ] …

## Tasks
- backend: …
- frontend: …
- tech-lead: review

## Open questions
- …

## Hand-off prompts
### backend
…
### frontend
…

## Notes
```
