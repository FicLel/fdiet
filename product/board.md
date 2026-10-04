# Board

Updated 2026-10-04.

## In progress
- FD-054 — backend done (V19, V20 applied, 5000 restarted); frontend running.
- FD-056 — frontend, same run after FD-054.
- FD-041, FD-052 — review done; awaiting user commit.

## Next up (order accepted 2026-10-04)
1. FD-043 — re-parse stored names. Migration, after FD-054.
2. FD-033 phase E — remove BEDCA.
3. FD-034 phase A — manifest + provenance.

## Blocked
_Nothing._

## Waiting on the user
- Commit FD-041 + FD-052.
- FD-060 (`cocinado` not read as cooked) — move ahead of FD-056?
- FD-052 outcome: no mismatch ever totalled, even with a yield — keep?
- Backend on 5000: restart after FD-054 and FD-043 migrations (agents restart it during live checks).
- FD-008 — run `DROP DATABASE fdiet_ui_verify;` yourself (agent drop blocked by permissions).
