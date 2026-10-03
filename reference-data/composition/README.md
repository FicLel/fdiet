# Open composition data

The open food composition tables fdiet is moving to (FD-033), and the files that tie them to the
Spanish a diet is written in. Loaded into `composition_foods` by `POST /api/composition/sync` and,
when `fdiet.composition.sync-on-startup=true`, at startup.

| Folder | What | Licence |
| --- | --- | --- |
| `ciqual-2025/` | ANSES CIQUAL 2025, unmodified upstream files | CC BY 4.0 |
| `bls-4.0/` | Max Rubner-Institut BLS 4.0, unmodified upstream files | CC BY 4.0 |
| `usda-sr-legacy/` | refuse % per SR Legacy food, an extract | CC0 1.0 |
| `composition-es/` | fdiet's Spanish names (`links.csv`) and the CIQUAL foods without energy | CC BY 4.0 |

`manifest.csv` records, per file: where it came from (URL or landing page, DOI), when, its size,
its SHA-256 and its SPDX licence id. Upstream files are kept byte for byte (`.gitattributes`
switches off line-ending conversion for them) so a hash can be re-checked at any time. fdiet's own
work never goes inside an upstream file; it lives in `composition-es/`.

Every folder has a `LICENSE.md` with the attribution its figures need. The sync answers with the
CIQUAL and BLS attribution strings.

**Git LFS.** The `.xlsx` and `.pdf` snapshots (about 17 MB, 14 MB of it the BLS table) are stored
through Git LFS, so a new installation needs `git lfs install` before `git clone` (or
`git lfs pull` after it). Without it those files are LFS pointer files, the hashes in `manifest.csv`
do not match, and the composition sync and `CompositionDataFilesTest` fail. LFS keeps the files
byte for byte, so the recorded SHA-256 still holds.
