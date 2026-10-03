# Board

Updated 2026-10-03.

## In progress
- FD-033 phase B — implemented + tech-lead reviewed 2026-10-03 (299 tests green), **uncommitted**. Crosswalk 122/122 approved; originals in Git LFS. Follow-ups → FD-038.

## Next up
- FD-033 phases C–E (BEDCA removed last).
- FD-034 phase A (manifest + provenance) — no dependency, can go any time.

## Blocked
_Nothing._

## Waiting on the user
- Prioritise the rest of the backlog (FD-010…FD-032, FD-036, FD-037).
- FD-008 — `DROP DATABASE fdiet_ui_verify;`
- FD-033 B — commit; re-sync the dev DB (`POST /api/composition/sync`) so `name_reviewed` turns true.
