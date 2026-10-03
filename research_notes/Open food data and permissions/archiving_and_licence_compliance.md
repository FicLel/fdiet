# Snapshotting third-party open data into fdiet's repository: archiving and licence compliance

Scope: how fdiet (Spanish, open-source, diet planning; EU/Spain jurisdiction) should keep CSV snapshots of
third-party data in git so they survive link rot, while staying inside each licence. Current state of the
repo (read 2026-10-02): `bedca_foods.csv` + `BEDCA-ATTRIBUTION.txt` at the root; `reference-data/` with one
folder per source (`aesan-2022/`, `aesan-mec-2010/`, `5aldia-2019/` with `LICENSE.md`, `usda-yields-2014/`
with `LICENSE.md`) and a manifest `reference-data/sources.csv` with columns
`code,short_name,title,institution,country,tier,year,url,licence_class,licence,attribution,clinical,retrieved_on,notes`.
Not legal advice. Research date 2026-10-02.

## 1. Licence obligations and compatibility when several datasets live side by side

### Takeaway
Storing datasets in separate folders and loading them into separate rows/tables of one database is, under
both ODbL and CC 4.0, a *collection/collective database* rather than an adaptation, so each licence stays
contained — *as long as each source's rows are kept unmodified and separable*. The moment fdiet adds its own
columns to a source's rows (as `5aldia-2019/` already does with `bedca_food_id`), that file is adapted
material and, for BY-SA/ODbL, must itself go out under the same licence (which fdiet already does for
5 al día). BEDCA's "no modification + non-commercial + express authorisation for electronic use" terms are
the binding constraint on the whole app, not the open licences.

### Cited Findings
**CC BY-SA 4.0 (5 al día)**
- Attribution elements to retain: creator identification, copyright notice, licence notice, disclaimer
  notice, URI to the material "to the extent reasonably practicable", and an indication of modifications;
  may be satisfied "in any reasonable manner based on the medium, means, and context" (§3(a)(1)–(2)) —
  [CC BY-SA 4.0 legal code](https://creativecommons.org/licenses/by-sa/4.0/legalcode.en)
- §4 Sui generis database rights: you may extract, reuse, reproduce and share all or a substantial portion
  of the database; if you include all or a substantial portion of the contents in a database in which you
  have sui generis rights, *that database* is Adapted Material (so ShareAlike applies to it) —
  [CC BY-SA 4.0 legal code](https://creativecommons.org/licenses/by-sa/4.0/legalcode.en)
- ShareAlike for adaptations: same licence elements, this version or later, or a "BY-SA Compatible
  License"; no additional restrictions — [CC BY-SA 4.0 legal code](https://creativecommons.org/licenses/by-sa/4.0/legalcode.en)
- CC's data guidance: "The SA licenses require you to apply the same or a compatible license to any
  database you share publicly and in which you include a substantial portion of the licensed database
  contents. Note that this does **not** require you to ShareAlike any copyright or other rights you have in
  the individual contents of the database." — [CC wiki: Data](https://wiki.creativecommons.org/wiki/Data)
- "Facts are not subject to copyright, nor are the ideas underlying copyrighted content." — [CC wiki: Data](https://wiki.creativecommons.org/wiki/Data)
- In CC 4.0, applicable sui generis database rights are licensed under the same conditions as copyright;
  collections may be licensed differently from their components, unlike adaptations —
  [CC FAQ](https://creativecommons.org/faq/)
- The only one-way compatible licences approved for BY-SA 4.0 are GPLv3 and Free Art License 1.3; no
  compatibility with ODbL, OGL or Etalab is listed — [CC compatible licenses](https://creativecommons.org/share-your-work/licensing-considerations/compatible-licenses/)
- CC "does not recommend use of its NonCommercial (NC) or NoDerivatives (ND) licenses on databases intended
  for scholarly or scientific use." — [CC wiki: Data](https://wiki.creativecommons.org/wiki/Data)

**ODbL 1.0**
- "Collective Database": the Database *in unmodified form* as part of a collection of independent
  databases assembled into a collective whole. "Derivative Database": any translation, adaptation,
  arrangement, modification or other alteration of the Database or of a Substantial part of the Contents.
  "Produced Work": an image, text, etc. resulting from using the whole or a substantial part of the
  contents via a query — [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/)
- §4.2 notices (licence copy/URI, keep notices); §4.3 a Produced Work used publicly needs a notice
  "reasonably calculated" to tell people the content comes from the database and is under ODbL; §4.4
  publicly used Derivative Databases must be ODbL, a later version, or a compatible licence; §4.5 share-alike
  does not apply to a Collective Database or internal use; §4.6 a public Derivative Database requires
  offering the whole modified database or a file of alterations —
  [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/)
- The ODbL FAQ distinguishes the database from "produced works" (e.g., a map made from the data); share-alike
  attaches to derivative databases — [ODC licences FAQ](https://opendatacommons.org/faq/licenses/)

**Etalab Licence Ouverte 2.0**
- Designed to be compatible with any licence requiring at least attribution; explicitly compatible with
  OGL (UK), CC BY and ODC-BY — [SPDX etalab-2.0](https://spdx.org/licenses/etalab-2.0.html); text at
  [data.gouv.fr](https://www.data.gouv.fr/pages/legal/licences/etalab-2.0) and
  [PDF](https://www.etalab.gouv.fr/wp-content/uploads/2017/04/ETALAB-Licence-Ouverte-v2.0.pdf)
- Note: the old canonical page `etalab.gouv.fr/licence-ouverte-open-licence/` now 301-redirects to the
  data.gouv.fr home page (checked 2026-10-02) — a live example of licence-page link rot —
  [etalab.gouv.fr URL](https://www.etalab.gouv.fr/licence-ouverte-open-licence/)

**OGL v3**
- Default attribution "Contains public sector information licensed under the Open Government Licence
  v3.0." plus link to the licence where possible; commercial use permitted; no share-alike; compatible with
  CC BY 4.0 and ODC-BY (complying with those satisfies OGL); excludes personal data, logos/crests,
  third-party rights, etc. — [OGL v3](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/)

**CC BY-NC-SA 3.0 IGO (FAO and other intergovernmental bodies)**
- NonCommercial = not "primarily intended for or directed toward commercial advantage or private monetary
  compensation"; ShareAlike to this licence, a later version with the same elements, or an unported/ported
  CC licence with the same elements; must not imply connection or endorsement; disputes go to mediation then
  UNCITRAL arbitration and the licensor's privileges and immunities are preserved —
  [CC BY-NC-SA 3.0 IGO legal code](https://creativecommons.org/licenses/by-nc-sa/3.0/igo/legalcode)

**BEDCA (as recorded in the repo)**
- Attribution required wherever shown "including in application credits"; data may not be modified nor its
  meaning altered; personal, educational or non-commercial use only; "reproduction, translation or any other
  use in electronic format requires the express authorisation of AESAN/BEDCA" —
  `C:/Users/victo/workbench/fdiet/BEDCA-ATTRIBUTION.txt` (local, transcribing BEDCA's conditions;
  [bedca.net](https://www.bedca.net/))

### Inferences
Compatibility matrix — can material under the **row** licence be put *into an adaptation/derivative* that is
released under the **column** licence? ("Collection" = side by side, each keeps its own licence; always
possible for every pair except where the source forbids redistribution at all.)

| from \ into | CC0 | CC BY 4.0 | CC BY-SA 4.0 | ODbL 1.0 | Etalab 2.0 | OGL v3 | BY-NC-SA 3.0 IGO | BEDCA-type NC/ND |
|---|---|---|---|---|---|---|---|---|
| CC0 / US PD | yes | yes | yes | yes | yes | yes | yes | n/a (can't adapt) |
| CC BY 4.0 | no | yes | yes | yes* | yes* | yes* | yes | no |
| CC BY-SA 4.0 | no | no | yes | no | no | no | no | no |
| ODbL 1.0 | no | no | no | yes | no | no | no | no |
| Etalab 2.0 | no | yes | yes | yes | yes | yes | yes | no |
| OGL v3 | no | yes | yes | yes | yes | yes | yes | no |
| BY-NC-SA 3.0 IGO | no | no | no | no | no | no | yes (or other NC-SA w/ same elements) | no |
| BEDCA NC + no-modification | no | no | no | no | no | no | no | only verbatim copy, NC, with authorisation |

\* attribution-only licences can flow into a stricter licence provided the attribution is kept; that is the
general reading of attribution-only licences and is stated for Etalab/OGL in their own compatibility clauses;
for CC BY → ODbL it is an inference, not a CC statement.
- Consequences for fdiet:
  - Keep each source's rows **verbatim and separable** (own folder, own file, own `source_code` column in DB):
    then the repo and the DB are a collection/collective database, and BY-SA/ODbL share-alike does not
    spread to fdiet's other data or code.
  - Any file where fdiet *adds* data to a source's rows (e.g., `bedca_food_id` in `5aldia-2019/`) is an
    adaptation: keep it under the source's licence in that source's folder and list the changes (fdiet
    already does this in `5aldia-2019/LICENSE.md`, which satisfies CC "indicate modifications").
    Alternative: keep the source file pristine and put fdiet's joins in a separate fdiet-licensed file
    (`links.csv`) — then the source file stays unmodified and the mapping is fdiet's own work. For ODbL this
    still risks a Derivative Database if the joined result is "publicly used"; the safe ODbL pattern is to
    release the joined table under ODbL too.
  - Never put BY-SA and ODbL rows into one *derived* table (e.g., a merged "rations" table that averages
    them): no licence satisfies both share-alikes.
  - BY-NC-SA 3.0 IGO and BEDCA both make the app non-commercial for as long as their data are in it; they
    are mutually compatible only as a collection (BEDCA allows no adaptation at all). Code licence (fdiet's
    own) is unaffected because data and code are separate works — but state that explicitly in each
    `LICENSE.md` ("This licence covers these data files only", as 5 al día's already does).
  - A Produced Work (a screen showing computed rations) from ODbL data needs an on-screen notice
    (§4.3); from CC BY/BY-SA it needs attribution; the app's footer pattern already used for BEDCA covers this
    if it is generated from the manifest.
  - Per-source attribution strings to store in the manifest: CC (TASL: title, author, source URL, licence +
    "modified by fdiet: …"); OGL ("Contains public sector information licensed under the Open Government
    Licence v3.0." + provider's own statement); Etalab (producer + date of last update, per its paternité
    clause — see Gaps); datos.gob.es / Spanish PSI (source + last update date, not distorting meaning).

### Gaps
- Could not fetch the Etalab 2.0 text itself (GitHub 503, etalab.gouv.fr redirect). The "source + date of
  last update" attribution wording is from memory of the licence and is consistent with SPDX's summary, but
  should be verified against the [data.gouv.fr text](https://www.data.gouv.fr/pages/legal/licences/etalab-2.0).
- No official CC or ODC statement found on CC BY 4.0 → ODbL compatibility; CC BY-SA 4.0 ↔ ODbL is
  incompatible on the face of both texts (neither lists the other).
- Whether adding a foreign-key column to a CC BY-SA table creates an "adaptation" in the copyright sense (as
  opposed to the database sense) is unsettled; fdiet's conservative treatment (treat as adaptation) is safe.

## 2. EU/Spanish law: may a project copy published tables without asking?

### Takeaway
Partly. Individual facts (a food weighs 60 g; 30 g of fibre) are not protected, and copying an
*insubstantial* part of a database is lawful for a lawful user. But copying a whole published table (e.g., all
of BEDCA, a full rations annex) is an extraction of a substantial part, protected by the sui generis right for
15 years if the maker made a substantial investment in obtaining/verifying/presenting it. Spanish PSI reuse law
gives a general authorisation only for public-sector documents *without third-party IP*, with conditions
(cite source, date, don't distort). The TDM exceptions allow copying for analysis but **not** redistribution, so
they do not cover committing a dataset to a public git repository.

### Cited Findings
- LPI art. 133 (sui generis right; Directive 96/9/EC art. 7): protects the maker's substantial investment and
  lets them forbid extraction/reuse of the whole or a substantial part; repeated systematic extraction of
  insubstantial parts is also forbidden when it conflicts with normal exploitation. Art. 134: the lawful user
  may extract/reuse insubstantial parts without authorisation; art. 135: exceptions (private purposes for
  non-electronic databases, teaching/scientific research illustration with source cited and non-commercial
  aim, public security/administrative procedures); art. 136: 15 years from completion/publication, renewed by
  a substantial modification — [LPI consolidated text, BOE](https://www.boe.es/buscar/act.php?id=BOE-A-1996-8930)
  (the WebFetch summaries of this page were garbled; the article contents above match the Directive and should
  be re-read from the BOE text before quoting)
- LPI art. 13: legal and regulatory provisions and court decisions are not protected by IP —
  [LPI, BOE](https://www.boe.es/buscar/act.php?id=BOE-A-1996-8930)
- CJEU C-203/02 *British Horseracing Board v William Hill* (9 Nov 2004): investment in *creating* the data does
  not count; only investment in obtaining (finding existing data), verifying and presenting the contents can
  ground the sui generis right — [EUR-Lex 62002CJ0203](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=celex%3A62002CJ0203);
  [ipcuria summary](https://www.ipcuria.eu/case?reference=C-203%2F02)
- Open Data Directive 2019/1024 art. 1(6): "The right for the maker of a database provided for in Article 7(1)
  of Directive 96/9/EC shall not be exercised by public sector bodies in order to prevent the re-use of
  documents or to restrict re-use beyond the limits set by this Directive." —
  [legislation.gov.uk copy of Directive 2019/1024](https://www.legislation.gov.uk/eudr/2019/1024/data.xht?view=snippet&wrap=true);
  [Kluwer Copyright Blog](https://legalblogs.wolterskluwer.com/copyright-blog/please-share-nicely-from-database-directive-to-data-governance-acts/)
- Ley 37/2007 (reuse of public sector information): art. 3.3 excludes documents over which third parties hold
  IP rights; art. 4 modalities (no conditions, general conditions, licences, request); art. 8 general
  conditions — cite the source, do not distort the meaning, state the date of last update; open licences with
  minimum restrictions are preferred after the 2021 amendment (RDL 24/2021 transposed Directive 2019/1024) —
  [Ley 37/2007, BOE](https://www.boe.es/buscar/act.php?id=BOE-A-2007-19814);
  [RDL 24/2021, BOE](https://www.boe.es/diario_boe/txt.php?id=BOE-A-2021-17910)
- datos.gob.es aviso legal: general authorisation to reuse under Ley 37/2007 and RD 1495/2011; required
  credit "Origen de los datos: …"; "Está prohibido desnaturalizar el sentido de la información"; mention the
  date of last update when the original carries it; the page fetched did not name CC BY 4.0 as the licence —
  [datos.gob.es aviso legal](https://datos.gob.es/es/aviso-legal)
- DSM Directive 2019/790: art. 2(2) TDM = "automated computational analysis of information in digital form";
  art. 3 for research organisations and cultural heritage institutions (scientific research, copies kept
  securely for verification); art. 4 general TDM exception for lawfully accessible content, retained as long as
  necessary, unless the rightholder reserved it by machine-readable means; neither authorises making copies
  available to the public — [Directive 2019/790, EUR-Lex](https://eur-lex.europa.eu/eli/dir/2019/790/oj/eng)
- LPI art. 67 (inserted by RDL 24/2021): no authorisation needed for reproductions of lawfully accessible works
  for TDM; does not apply where rightholders expressly reserved use, e.g. by machine-readable means (67.3) —
  [RDL 24/2021, BOE](https://www.boe.es/diario_boe/txt.php?id=BOE-A-2021-17910);
  [akme.es commentary](https://akme.es/limite-de-mineria-de-textos-y-datos/)
- AESAN's aviso legal URL (`aesan.gob.es/AECOSAN/web/aecosan_aviso_legal.htm`) returns HTTP 404 on 2026-10-02
  (also recorded as 404 on 2026-09-13 in `reference-data/sources.csv`); the Wayback Machine has a 200 snapshot
  from 2025-12-07: `http://web.archive.org/web/20251207094553/https://www.aesan.gob.es/AECOSAN/web/aecosan_aviso_legal.htm` —
  [Wayback availability API](https://archive.org/wayback/available?url=aesan.gob.es/AECOSAN/web/aecosan_aviso_legal.htm)

### Inferences
- **Facts route:** a handful of figures re-keyed from a guideline (a rations table of ~20 rows from a 60-page
  report) is a strong candidate for "insubstantial part" + unprotected facts, and fdiet's existing practice of
  citing document and page is the right hygiene. The original *expression* (layout, prose, food photos) is not
  copied, so copyright in the PDF is not engaged. This does not help for whole databases (BEDCA, a full
  household-measures database).
- **PSI route (AESAN, Ministerio de Sanidad, datos.gob.es):** AESAN is a public body, so Ley 37/2007 plus
  Directive 2019/1024 art. 1(6) mean it should not use the database right to block reuse of its own
  documents — but only for documents that are within scope (not third-party IP: a committee report authored by
  external scientists, or BEDCA, whose terms are set by the BEDCA network/AESAN with an explicit NC clause, may
  be argued either way). Where a body has published its own *specific* conditions (BEDCA), those govern; the
  general PSI conditions do not override an explicit licence. Flag: uncertain, worth a permission email.
- **TDM route:** it lets fdiet *download and analyse* a dataset (e.g., to compute candidate matches) but not
  *commit and publish* it. Do not rely on art. 67 for the repository snapshot.
- **Archive the licence page as well as the data.** The AESAN case shows the licence terms rot first; the
  snapshot of the terms is the evidence of what was granted on the retrieval date.

### Gaps
- Exact wording of LPI arts. 133–135 and art. 67 could not be cleanly extracted (fetch summaries were
  unreliable); verify from BOE before quoting.
- Could not read the archived AESAN aviso legal (web.archive.org blocked for the fetch tool), so the precise
  AESAN reuse clause (and whether it excludes commercial use) is unverified; the repo's paraphrase ("reproducción
  total o parcial citando la fuente y la fecha de la última actualización, sin alterar el contenido") stays
  unconfirmed.
- No case law found on whether a nutrition-reference table counts as a "substantial investment" database.

## 3. Practical archiving and a manifest/re-fetch workflow

### Takeaway
Use three layers: (1) the git repo itself (the CSV snapshot, plus — where the licence allows redistribution —
the source PDF/XML), archived automatically by Software Heritage; (2) a Wayback Machine (and optionally
archive.today) capture of every source URL *and* every licence/terms page, recorded in the manifest; (3) for
datasets whose licence permits redistribution, a Zenodo deposit with a DOI. Record per file: source URL,
archived URL, retrieval date, SHA-256, bytes, licence id, licence URL (+ archived licence URL), attribution,
modifications. Frictionless `datapackage.json` per source folder fits this well; fdiet's `sources.csv` can stay
as the human-readable index.

### Cited Findings
- SPN2 API: `POST https://web.archive.org/save`, header `Authorization: LOW <accesskey>:<secret>` (keys from
  archive.org account settings); options include `capture_all=1` (also capture 4xx/5xx), `capture_outlinks=1`
  (one level deep, applies to PDF/JSON/RSS too), `skip_first_archive`, `outlinks_availability=1` —
  [SPN2 API docs gist](https://gist.github.com/regstuff/82e690db2f1d91ba59f6681c1abad6cf);
  [Big Iron guide](https://www.bigiron.cc/guides/wayback-machine-apis-cdx-save-page-now-and-availability);
  [savepagenow Python wrapper](https://palewi.re/docs/savepagenow/index.html)
- Availability API: `https://archive.org/wayback/available?url=<url>` returns the closest snapshot URL,
  timestamp and status (used successfully above for the AESAN notice) —
  [Wayback availability API](https://archive.org/wayback/available?url=aesan.gob.es/AECOSAN/web/aecosan_aviso_legal.htm)
- Software Heritage archives source code repositories, offers "Save Code Now" for on-demand archiving, and gives
  intrinsic SWHIDs at several granularities — [Software Heritage](https://www.softwareheritage.org/save-and-reference-research-software/)
- Zenodo: anyone may deposit "content for which they possess the appropriate rights"; access open, embargoed,
  restricted or closed; DOIs persistent, file versioning; 50 GB per record; retained for the lifetime of the
  repository (CERN, "experimental programme defined for the next 20 years at least"); withdrawn records keep a
  tombstone at the DOI — [Zenodo policies](https://about.zenodo.org/policies/)
- Frictionless Data Resource: `name`, `path`, `profile`, `title`, `description`, `format`, `mediatype`,
  `encoding`, `bytes`, `hash` (MD5 by default; other algorithms by prefix, e.g. `"sha1:…"`, so `"sha256:…"`),
  `sources` and `licenses` inherited from the package unless overridden —
  [Frictionless Data Resource spec](https://specs.frictionlessdata.io/data-resource/)

### Inferences
- **What to store:**
  - Extracted CSV — always (that is fdiet's working data), with a `source_page` column as now.
  - Source PDF/XML in the repo — only when the licence allows redistribution of the document itself (CC BY,
    CC BY-SA, CC0/PD, OGL, Etalab, Spanish PSI without restrictions). For BEDCA (electronic reproduction needs
    authorisation) and permission-required sources, store only the checksum + archived URL, not the file.
    Large PDFs: consider Git LFS or a Zenodo/IA item instead of bloating history.
  - Licence/terms page — always capture to Wayback and record the snapshot; for open licences store the
    licence text (`LICENSE.md` with the legal code link) in the folder.
- **Zenodo only for redistributable data**; a "restricted" Zenodo record could hold a private evidence copy of
  a permission-required source, but that is still a copy and needs the holder's consent. BEDCA should not be
  deposited publicly without AESAN authorisation.
- **Software Heritage** will capture the whole public repo (CSV snapshots included) once it is crawled or
  "Save Code Now" is triggered; cite the SWHID of the data folder in the manifest's README for a
  content-addressed reference.
- **archive.today** has no official API and an unclear operator/terms; use it as a manual second copy only.
- **Recommended manifest** — `reference-data/<source>/datapackage.json` per folder, plus keep
  `reference-data/sources.csv` as the index (add the columns below). Proposed fields:

```json
{
  "name": "5aldia-2019",
  "title": "Tamaño de raciones de frutas y hortalizas (5 al día, 2019), Tabla 4",
  "licenses": [{"name": "CC-BY-SA-4.0", "path": "https://creativecommons.org/licenses/by-sa/4.0/",
                 "title": "Creative Commons Attribution-ShareAlike 4.0"}],
  "sources": [{"title": "Russolillo G et al. Rev Esp Nutr Hum Diet 2019;23(4):205-221",
               "path": "https://doi.org/10.14306/renhyd.23.4.628"}],
  "x-fdiet": {
    "attribution": "Russolillo G, Baladia E, Moñino M, et al. (2019) ... CC BY-SA 4.0. Adaptado por fdiet.",
    "licence_class": "A-open-sharealike",
    "licence_evidence_url": "https://web.archive.org/web/<ts>/https://renhyd.org/...",
    "permission_file": null,
    "redistribute_original": true,
    "commercial_use": true,
    "modifications": ["bedca_food_id column added", "household measures restated as measure+size+count"]
  },
  "resources": [{
    "name": "rations", "path": "rations.csv", "format": "csv", "mediatype": "text/csv",
    "encoding": "utf-8", "bytes": 12345, "hash": "sha256:<hex>",
    "x-origin": {
      "url": "https://renhyd.org/.../628.pdf", "pages": "214-215",
      "archived_url": "https://web.archive.org/web/<ts>/https://renhyd.org/...",
      "original_sha256": "<hex of the PDF as downloaded>",
      "retrieved_on": "2026-09-13", "retrieved_by": "victor", "method": "manual transcription, checked"
    }
  }]
}
```
  Columns to add to `sources.csv` if staying CSV-only: `licence_id` (SPDX where one exists: `CC-BY-4.0`,
  `CC-BY-SA-4.0`, `ODbL-1.0`, `etalab-2.0`, `OGL-UK-3.0`, `CC0-1.0`, `CC-BY-NC-SA-3.0-IGO`, else
  `LicenseRef-BEDCA`), `licence_url`, `licence_archived_url`, `archived_url`, `original_sha256`,
  `snapshot_sha256`, `permission_file`, `redistribute_original`, `commercial_use`, `modifications`.
- **Re-fetch workflow** (`scripts/check-sources` run by hand or in CI, monthly):
  1. For each source: `HEAD`/`GET` `url` and `licence_url`; record status. 404/410/301-to-home = **flag link
     rot** (open an issue; the repo copy + `archived_url` remain the record — do not delete data).
  2. If 200: download, compute SHA-256, compare with `original_sha256`. Same → OK. Different → **flag
     "upstream changed"**: a new version is a human decision (re-transcribe, diff, bump `retrieved_on`), never
     auto-overwrite (BEDCA forbids modification; reference rows have stable codes).
  3. Recompute `snapshot_sha256` of each committed CSV; mismatch without a manifest update = **flag
     "local edit not recorded"** (catches silent modification of BEDCA).
  4. If no Wayback capture newer than N months, call SPN2 for `url` and `licence_url`; write the returned
     snapshot URL back to the manifest (or print it for a human to commit).
  5. Validate each `datapackage.json` (Frictionless `frictionless validate`) so schema/hash drift is caught.
  6. Print an attribution report generated from the manifest — the same strings the UI footer shows.

### Gaps
- Did not verify current SPN2 rate limits or anonymous-use quotas (the canonical Google Doc did not render);
  the `if_not_archived_within` parameter is commonly cited but not confirmed in the sources fetched.
- Software Heritage page did not detail file-type coverage; that it archives all files in a git repo (data
  included) is my understanding of how it ingests repositories, not verified here.
- archive.today's terms and longevity were not researched.

## 4. Permission-request email template (Spanish) and recording the permission

### Takeaway
Ask for a written, specific grant covering: (a) storing and redistributing the extracted table in a public
open-source repository; (b) use inside the app (shown to patients); (c) commercial vs non-commercial; (d) the
exact attribution wording; (e) whether fdiet may derive values (rations, unit conversions, links to BEDCA ids)
and how those must be labelled; (f) duration and revocation. Record the reply verbatim in the source folder and
point to it from the manifest.

### Cited Findings
- Licences that grant less than needed must be supplemented by permission: BEDCA requires "express
  authorisation" for electronic use and commercial use — `BEDCA-ATTRIBUTION.txt` (local); BY-NC-SA 3.0 IGO forbids
  implying endorsement without separate written permission —
  [CC BY-NC-SA 3.0 IGO](https://creativecommons.org/licenses/by-nc-sa/3.0/igo/legalcode)
- Spanish PSI law allows reuse via specific licence or prior request (art. 4 modalities) —
  [Ley 37/2007](https://www.boe.es/buscar/act.php?id=BOE-A-2007-19814)

### Inferences
Template (adapt names/sources):

> **Asunto:** Solicitud de autorización para reutilizar [nombre de la tabla/base de datos] en un proyecto de código abierto (fdiet)
>
> Estimado/a [nombre o "equipo de …"]:
>
> Me llamo Víctor Martínez y desarrollo **fdiet**, una aplicación de código abierto para que dietistas-nutricionistas
> redacten planes semanales de alimentación y cuenten raciones. El código está en [URL del repositorio].
>
> Querríamos utilizar **[título exacto, autores, año, tabla/anexo y páginas; DOI o URL]**. En concreto:
>
> 1. **Almacenamiento y redistribución:** transcribir [n.º] filas de [tabla] a un fichero CSV y guardarlo en el
>    repositorio público del proyecto, de modo que cualquiera que descargue el código reciba también el fichero.
>    ¿Podemos incluir también una copia del documento original (PDF) o solo los datos extraídos?
> 2. **Uso en la aplicación:** mostrar estas cifras a profesionales y a sus pacientes dentro de fdiet.
> 3. **Carácter comercial:** el proyecto es gratuito y sin ánimo de lucro [actualmente]. ¿La autorización cubriría
>    también un eventual uso comercial (por ejemplo, un servicio de pago alojado), o ese caso requeriría una nueva
>    solicitud?
> 4. **Modificaciones y datos derivados:** no alteraríamos los valores publicados. Sí calcularíamos a partir de
>    ellos (p. ej., convertir kJ a kcal, escalar por ración, enlazar cada alimento con su código BEDCA, expresar una
>    medida casera como número de unidades). Estos cálculos se harían al leer y se identificarían como obra de fdiet,
>    no de [titular]. ¿Es aceptable?
> 5. **Atribución:** ¿qué texto de cita desean que aparezca en la aplicación y en el repositorio? Proponemos:
>    «Fuente: [cita]. Reproducido con autorización de [titular] ([fecha]). Los cálculos derivados son de fdiet.»
> 6. **Licencia y duración:** ¿bajo qué condiciones y por cuánto tiempo se concede? Si prefieren, podemos publicar
>    los datos bajo una licencia abierta que ustedes indiquen (p. ej., CC BY 4.0).
>
> Si nos autorizan, guardaremos esta correspondencia en el repositorio junto a los datos, para que quede constancia de
> las condiciones. Comunicaremos cualquier error que detectemos en los datos a [contacto, p. ej. bedca.adm@gmail.com].
>
> Muchas gracias por su tiempo.
> Un saludo,
> Víctor Martínez — [correo] — [URL del proyecto]

Recording the permission:
- `reference-data/<source>/PERMISSION.md`: who granted it (name, role, institution), date, channel, the request
  as sent and the reply **verbatim** (headers trimmed of personal contact data if the grantor prefers), and a
  plain summary of scope (redistribution: yes/no; commercial: yes/no; modification/derived: yes/no; attribution
  text; expiry/revocation). Optionally attach the email as `.eml`/PDF.
- Manifest: `permission_file: "PERMISSION.md"`, `licence_id: "LicenseRef-<source>-permission-YYYY-MM-DD"`, and
  `commercial_use` / `redistribute_original` set from the grant; the startup loader or CI can refuse to ship a
  source with `licence_class` = permission-required and no `permission_file`.
- If refused or no answer: keep the source out of `reference-data/` (as fdiet does for SENC, DIAL, FINUT) and log
  the request date in `plan.md`/the manifest so it is not re-asked blindly.

### Gaps
- No primary source found on how Spanish public bodies (AESAN, BEDCA network) formally answer reuse requests
  or whether they issue standard licence documents; the template is a reasoned draft.
- Data-protection note (GDPR): storing the grantor's email in a public repo is personal data; I did not research
  this further — minimise to name/role/institution unless the grantor agrees.
