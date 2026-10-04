# Board

Updated 2026-10-04.

## In progress
- None. FD-033 E starts after the commit below (big removal; keep it off a dirty tree).

## Next up (order accepted 2026-10-04)
1. FD-033 phase E — remove BEDCA.
2. FD-034 phase A — manifest + provenance.

## Blocked
- None.

## Waiting on the user
- Commit FD-060 + FD-043 (src/, V21 already applied to dev DB), FD-054 frontend and FD-056 (UI/): done, reviewed, live-checked, uncommitted.
- FD-052 outcome: no mismatch ever totalled, even with a yield — keep?
- FD-008 — run `DROP DATABASE fdiet_ui_verify;` yourself (agent drop blocked by permissions).
- FD-069 — was the unsaved "Prueba 1" draft ("2 huevos") yours?
- FD-072 — re-read stored `raw_name` for the 298 recipes without text (what FD-043 meant to fix)? Proposed: yes, after FD-073.
- Backend on 5000 runs the agent's bootRun (pid 7124, new build, V21); keep it or restart your own.
