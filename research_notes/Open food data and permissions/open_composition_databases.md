# Open food composition databases that could complement or replace BEDCA (as of 2026-10-02)

Scope: generic (not branded) composition tables a Spanish diet-planning app could store as CSV in a public git repo. For each: publisher, version, size, nutrients, licence, download, languages, edible-portion / state / household-measure data, and how well its rows map to foods like "lechuga", "pollo, pechuga, plancha", "pan de molde".

Verification note: everything with a link was read in a search result or a fetched page on 2026-10-02. Some official pages (fineli.fi, the Frida download page, one Mattilsynet page) returned 403, 429 or "Loading…", so a few facts come from search snippets or mirrors. These are marked **[snippet]**. Claims I could not source are in the Gaps sections.

## Q1. Which databases are open enough to ship in a public repo, and which are safe if fdiet goes commercial?

### Takeaway
Five generic European tables now ship under attribution-only licences that allow commercial use: **CIQUAL 2025**, **BLS 4.0** (newly open since Dec 2025), **Fineli**, the **Swedish Livsmedelsdatabasen**, **Matvaretabellen**, plus **CoFID** under OGL v3. On top of those, **USDA FoodData Central** is CC0. **FAO/INFOODS** is non-commercial, so is **BEDCA**. **NEVO**, **Frida** and the **Italian BDA** carry use conditions that rule them out or make them unclear for redistribution.

### Cited Findings

**Commercial-safe (attribution-only or public domain)**

| Database | Publisher | Version / date | Foods | Components | Licence | Commercial | Share-alike |
|---|---|---|---|---|---|---|---|
| CIQUAL 2025 | ANSES (France) | v1, published 2025-11-19 (data dated 2025-11-03) | 3,484 | 74 | CC BY 4.0 **and** Etalab Licence Ouverte 2.0 | Yes | No |
| BLS 4.0 | Max Rubner-Institut (Germany) | 4.0, available since 2025-12-15 | 7,140 | 138 | CC BY 4.0 | Yes | No |
| Fineli | THL (Finland) | Release 20 (2019-06-27) per snippet; may be newer | 4,232 | 55 (pkg 1) / 74 (pkg 2) | CC BY 4.0 | Yes | No |
| Livsmedelsdatabasen | Livsmedelsverket (Sweden) | page reviewed 2026-01-13 | ~2,500 | >50 | CC BY 4.0 | Yes | No |
| Matvaretabellen | Mattilsynet (Norway) | current version Jan 2026; annual | not found | not found | NLOD 2.0 | Yes (NLOD is an open licence) | No |
| CoFID (McCance & Widdowson) | PHE (now OHID), UK | 2021-03-19 | not stated on page | — | Open Government Licence v3.0 | Yes | No |
| FoodData Central | USDA ARS | Foundation Apr 2026; SR Legacy Apr 2018 (final); FNDDS Oct 2024 | see Q2 | — | CC0 1.0 | Yes | No |
| EFSA food composition DB for nutrient intake | EFSA | Zenodo 438313 | 7 countries | 15 vitamins and minerals only | CC BY 4.0 | Yes | No |

Sources for this table:
- CIQUAL 2025 is 3,484 foods and 74 components, under "Creative Commons Attribution 4.0 International and Etalab Open License 2.0", version 1 published 2025-11-19 — [Zenodo record 17550133](https://zenodo.org/records/17550133). Also hosted at [Recherche Data Gouv, doi:10.57745/RDMHWY](https://entrepot.recherche.data.gouv.fr/dataset.xhtml?persistentId=doi%3A10.57745%2FRDMHWY).
- The old CIQUAL 2020 dataset on data.gouv.fr (Licence Ouverte, last updated 2020-07-03) says it is "cancelled and replaced" by the recherche.data.gouv version — [data.gouv.fr Ciqual](https://www.data.gouv.fr/fr/datasets/table-de-composition-nutritionnelle-des-aliments-ciqual/).
- The 2025 changes: 7 new vitamin components, more than 113,000 new data points, about 300 new products (skyr, sweet-potato fries, tacos), and revised breads, cereals, cheeses and processed meats — [ANSES 2025 table page](https://ciqual.anses.fr/cms/en/2025-anses-ciqual-table).
- BLS 4.0:
  - Licence is "CC BY 4.0 (Creative Commons Namensnennung 4.0 International)", https://creativecommons.org/licenses/by/4.0/deed.de. The "Max Rubner-Institut [is] als Herausgeber zu nennen". DOI 10.25826/Data20251217-134202-0, file `BLS_4_0_2025_DE.zip` — [blsdb.de/download](https://blsdb.de/download).
  - 7,140 foods and 138 nutrients, available since 2025-12-15, with the former licence fee dropped — [heise.de news](https://www.heise.de/en/news/Bundeslebensmittelschluessel-4-0-Nutrient-database-now-publicly-available-11123877.html), [MRI press release](https://www.mri.bund.de/de/aktuelles/meldungen/meldungen-einzelansicht/gemeinsame-pressemitteilung-bmleh-und-mri/) **[snippet]**.
- Fineli: the content is "freely available under CC-BY 4.0". Basic package 1 is 4,232 foods × 55 components; package 2 is 4,232 × 74. Names are in Finnish, Swedish and English, files are character-separated CSV, and the packages include household measures and portion sizes — [Fineli Open Data](https://fineli.fi/fineli/en/avoin-data), [Fineli help 19](https://fineli.fi/fineli/en/ohje/19) **[snippet; official page returned 403]**. Git mirror (CC-BY-4.0) with `basic-package-1`, `basic-package-2` and `ingredients-for-food-industry` — [theel0ja/fineli-data](https://github.com/theel0ja/fineli-data).
- Livsmedelsdatabasen:
  - Licence is CC BY 4.0, and "The Swedish Food Agency must be stated as the source".
  - About 2,500 foods with more than 50 nutrients, JSON over a REST API at https://dataportal.livsmedelsverket.se/livsmedel/swagger/index.html.
  - English names (British English) are included, the basis is "100 g edible part", and FoodEx2 and LanguaL codes are included.
  - [Livsmedelsverket Food Composition Data](https://www.livsmedelsverket.se/en/about-us/open-data/food-composition-data/).
- Matvaretabellen:
  - data.norge.no lists the licence as "Norwegian Licence for Open Government Data (NLOD 2.0)". It is downloadable as XLSX, CSV, JSON and EDN from matvaretabellen.no, values are "per 100 grams of edible portion", and it is updated annually — [data.norge.no](https://data.norge.no/en/datasets/9d082918-e3d4-4ae2-8efd-e7d025dfd52d/matvaretabellen).
  - Required citation: "Matvaretabellen 2026. Mattilsynet. www.matvaretabellen.no". The content is protected under åndsverkloven and must not be copied without clear source attribution — [Mattilsynet Opphavsrett](https://www.mattilsynet.no/mat-og-drikke/matvaretabellen/opphavsrett) **[snippet]**.
  - Current version published January 2026 — [Om gjeldande versjon](https://www.mattilsynet.no/mat-og-drikke/matvaretabellen/om-gjeldande-versjon) **[snippet]**.
- CoFID: "All content is available under the Open Government Licence v3.0, except where otherwise stated", Crown copyright. Excel main dataset (4.42 MB), legacy foods Excel, and a PDF user guide, updated 2021-03-19 — [gov.uk CoFID](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid).
- FoodData Central:
  - Released as public domain under CC0 1.0. Preferred citation: "U.S. Department of Agriculture, Agricultural Research Service. FoodData Central. https://fdc.nal.usda.gov" — [data.nal.usda.gov FoodData Central](https://data.nal.usda.gov/dataset/fooddata-central-0), [Ag Data Commons](https://agdatacommons.nal.usda.gov/articles/dataset/FoodData_Central/24668133) **[snippet]**.
  - The download page itself shows no licence statement — [FDC downloads](http://fdc.nal.usda.gov/download-datasets/).
- EFSA: "Food composition database for nutrient intake: selected vitamins and minerals in selected European countries", CC BY 4.0. Covers 15 vitamins and minerals (Ca, Cu, B12, Mg, niacin, P, K, riboflavin, thiamin, Fe, Se, B6, K, Zn, E) for FI, FR, DE, IT, NL, SE and UK — [Zenodo 438313](https://zenodo.org/records/438313), [data.europa.eu](https://data.europa.eu/data/datasets/food-composition-database?locale=en).

**Restricted, unclear, or non-commercial**
- **BEDCA**:
  - 950 foods, 48 components, coded with LanguaL and FoodEx2 — [El Aderezo summary](https://www.eladerezo.com/salud-y-bienestar/base-de-datos-espanola-de-composicion-de-alimentos.html) **[snippet, secondary]**. The non-commercial terms are at [bedca.net UsoBD.pdf](https://www.bedca.net/bdpub/UsoBD.pdf).
  - A datos.gob.es request (2026-04-14) asks for BEDCA in CSV or XML. Status "Assigned", last updated 2026-09-29, with no AESAN response documented. A commenter notes the official Spanish table "is the only one that cannot be reused" — [datos.gob.es request](https://datos.gob.es/en/solicitud-de-datos/base-de-datos-bedca).
  - The AESAN open-data "Alimentos y bebidas" set is a **branded** product database (products sold in Spain in 2022, Excel), not a generic BEDCA release. It cites BEDCA as a generic database "not managed by AESAN" — [AESAN datos abiertos](https://www.aesan.gob.es/en/datos-abiertos/alimentos-y-bebidas).
- **NEVO** (Netherlands, RIVM):
  - 2023/8.0 has 2,323 foods. The download is free but needs agreement to a dataset-use agreement.
  - Use is "only allowed if unchanged and when the source and version number are clearly stated", citing "NEVO-online version 2023/8.0, RIVM, Bilthoven". Listed uses are research, the food industry, dietetics and public-health education.
  - [RIVM request dataset](https://www.rivm.nl/en/dutch-food-composition-database/use-of-nevo-online/request-dataset), [RIVM copyright and disclaimer](https://www.rivm.nl/en/dutch-food-composition-database/use-of-nevo-online/copyright-and-disclaimer) **[snippet]**.
- **Frida** (Denmark, DTU Food):
  - Version 5.5, released 2025-12-19. Download is a spreadsheet, sent by e-mailed link after a form. Required credit "upon each display or use": "Food data (frida.fooddata.dk), version 5.5, 2025, National Food Institute, Technical University of Denmark". More than 1,100 foods, up to 113 nutrients.
  - [Frida data page](https://frida.fooddata.dk/data) **[snippet; fetch returned "Loading…"]**.
  - A "no profit-making activity" clause turned up in search, but it is the generic DTU Orbit text on a *publication PDF*, not verified as Frida's data terms — [DTU Orbit PDF](https://backend.orbit.dtu.dk/ws/portalfiles/portal/127145984/New_version_of_the_Danish_food_composition_databank_1.pdf).
- **Italy**:
  - CREA 2019 tables can be consulted freely online, for "scientific, educational, commercial…" purposes, provided the source is cited and IP is respected — [CREA banche dati](https://www.crea.gov.it/en/web/alimenti-e-nutrizione/banche-dati), [alimentinutrizione.it](https://www.alimentinutrizione.it/sezioni/tabelle-nutrizionali) **[snippet]**. I found no bulk download or explicit open licence.
  - The IEO **BDA** Excel needs a donation, at least €50 for personal or non-commercial research — [Fondazione IEO-Monzino BDA](https://www.fondazioneieomonzino.it/lp/banca-dati-alimenti-bda/) **[snippet]**.
- **Portugal**: INSA TCA v6.0 (2023) has 1,330 foods and 44 components, Excel downloadable from PortFIR. Access is "livre" (free) — [INSA news](https://www.insa.min-saude.pt/disponivel-nova-versao-da-tabela-de-composicao-de-alimentos/), [PortFIR](https://portfir-insa.min-saude.pt/) **[snippet]**. I found no named open licence.
- **FAO/INFOODS**:
  - The FAO/INFOODS composition databases are CC BY-NC-SA 3.0 IGO: copy, redistribute and adapt for **non-commercial** purposes with citation, and derivatives must be ShareAlike — [FAO/INFOODS West Africa table](https://openknowledge.fao.org/server/api/core/bitstreams/51ca3ee4-9bd4-4077-b746-93cecd34b2c0/content) **[snippet]**.
  - Older uFiSh/AnFooD wording: "for use in non-commercial products or services", with non-commercial use authorised free on request. uFiSh is at www.fao.org/fileadmin/templates/food_composition/documents/uFiSh1.0.xlsx — [FAO/INFOODS databases](https://www.fao.org/infoods/infoods/tables-and-databases/faoinfoods-databases/en/) **[snippet]**.
  - Density Database v2.0 has 638 entries — [FAO Density DB v2 PDF](https://www.fao.org/4/ap815e/ap815e.pdf).
- **Open Food Facts** is ODbL v1.0, distributed as dumps (JSON/CSV), SQLite and API — [emergentmind overview](https://www.emergentmind.com/topics/open-food-facts-dataset) **[secondary]**. Agribalyse's foods "are the same as those in the Ciqual" table — [Agribalyse doc](https://doc.agribalyse.fr/documentation-en/agribalyse-program/agribalyse-supporting-ecological-transition).

### Inferences
- **CIQUAL 2025 and BLS 4.0 are the two strongest BEDCA replacements**: large, recent, attribution-only, commercial-safe, and maintained by national agencies. Fineli is the strongest if household measures matter, because its open package includes them.
- Attribution-only licences (CC BY 4.0, Etalab 2.0, OGL v3, NLOD 2.0) are all compatible with fdiet's existing "attribution in the footer" pattern (`BEDCA-ATTRIBUTION.txt`). Each would need its own attribution file per source folder, like `reference-data/`.
- **FAO/INFOODS (NC-SA) adds two problems on top of BEDCA's**:
  - NC forbids commercial use.
  - SA would force any *derived CSV* (for example a merged table) to be released under the same licence.
  - Use it for research and cross-checks only, never mixed into a shipped CSV.
- **ODbL (Open Food Facts) is share-alike at the database level**. A derived database that is publicly used must be offered under ODbL; a "produced work" (a rendered figure) only needs attribution. Storing OFF rows in fdiet's repo alongside differently licensed data means keeping them in a separate file or folder under ODbL. (This is a general reading of ODbL, not checked against OFF's own terms page this session.)
- **NEVO's "only if unchanged" clause clashes with fdiet's derived-on-read design.** Storing the figures as published is fine, but showing kcal derived from kJ may be arguable. Avoid NEVO unless RIVM confirms.

### Gaps
- Fineli's current release number. Release 20 (2019) is the only one I confirmed; the official page returned 403.
- The exact Frida data licence text and whether commercial use is allowed were not verified.
- CoFID food count (believed to be about 2,900, not verified this session) and whether OHID has published a newer CoFID after 2021.
- USDA FDC licence: CC0 was confirmed only via the data.nal.usda.gov catalogue snippet. fdc.nal.usda.gov's own download page has no licence text.
- Matvaretabellen food and component counts were not retrieved (the download page returned 429).
- Exact ODbL obligations as stated by Open Food Facts were not fetched.

## Q2. Download URLs, formats, nutrients, and structural features (edible portion, raw/cooked, household measures)

### Takeaway
Every open table publishes per-100 g edible-portion values with energy, protein, fat, carbohydrate, fibre, sugars, water and sodium or salt. They differ in format (XLSX/XML vs CSV vs API-only) and in whether they ship household-measure weights. Fineli does, explicitly; for most of the others it is unverified.

### Cited Findings
- **CIQUAL 2025** files:
  - XML: `alim_2025_11_03.xml`, `alim_grp_2025_11_03.xml`, `compo_2025_11_03.xml`, `const_2025_11_03.xml`, `sources_2025_11_03.xml`.
  - XLSX and XLS spreadsheets, with an English version (`Table Ciqual 2025_ENG`) and an English PDF guide.
  - [Zenodo 17550133](https://zenodo.org/records/17550133).
  - Components include carbohydrates, starch and individual sugars, proteins, lipids and fatty acids, vitamins, minerals and energy — [Recherche Data Gouv search snippet](https://entrepot.recherche.data.gouv.fr/dataset.xhtml?persistentId=doi%3A10.57745%2FRDMHWY).
  - A separate "Table des aliments moyens Ciqual 2025" (averaged foods) also exists — [Recherche Data Gouv XOJCLN](https://entrepot.recherche.data.gouv.fr/dataset.xhtml?persistentId=doi%3A10.57745%2FXOJCLN).
- **FoodData Central** downloads, all CSV and JSON ZIPs under `https://fdc.nal.usda.gov/fdc-datasets/` ([FDC downloads](http://fdc.nal.usda.gov/download-datasets/)):
  - Foundation Foods: `FoodData_Central_foundation_food_csv_2026-04-30.zip`
  - SR Legacy: `FoodData_Central_sr_legacy_food_csv_2018-04.zip`, "the final release of the Standard Reference data type"
  - FNDDS (2021–2023 data): `FoodData_Central_survey_food_csv_2024-10-31.zip`
  - Branded: `FoodData_Central_branded_food_csv_2026-04-30.zip`
- **BLS 4.0**: ZIP `BLS_4_0_2025_DE.zip` — [blsdb.de/download](https://blsdb.de/download). Excel, 138 nutrients — [heise.de](https://www.heise.de/en/news/Bundeslebensmittelschluessel-4-0-Nutrient-database-now-publicly-available-11123877.html) **[snippet]**. The site has an English toggle, but English food names in the file were not confirmed.
- **Fineli**: CSV packages with FI/SV/EN names, household measures and portion sizes, plus an open JSON API — [Fineli open data](https://fineli.fi/fineli/en/avoin-data) **[snippet]**.
- **Livsmedelsdatabasen**: API-only (JSON), "100 g edible part", English names, FoodEx2 and LanguaL — [Livsmedelsverket](https://www.livsmedelsverket.se/en/about-us/open-data/food-composition-data/).
- **Matvaretabellen**:
  - XLSX, CSV, JSON and EDN from matvaretabellen.no, per 100 g edible portion.
  - Older 2022 files: https://www.matportalen.no/verktoy/matvaretabellen/article43467.ece/BINARY/Matvaretabellen%202022%20(xlsx)
  - [data.norge.no](https://data.norge.no/en/datasets/9d082918-e3d4-4ae2-8efd-e7d025dfd52d/matvaretabellen).
  - The source code of the official site is on GitHub — [Mattilsynet/matvaretabellen-deux](https://github.com/Mattilsynet/matvaretabellen-deux).
- **CoFID**: Excel main dataset plus legacy foods plus a 37-page PDF guide — [gov.uk](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid).
- **INSA TCA**: Excel via PortFIR menu "Composição de Alimentos > Descarregar Excel da TCA". It has 44 components: energy, macronutrients, fatty acids, cholesterol, vitamins, minerals — [INSA](https://www.insa.min-saude.pt/disponivel-nova-versao-da-tabela-de-composicao-de-alimentos/) **[snippet]**.
- **FAO/INFOODS density database**: lets gram weight be computed from volume (relevant to fdiet's "1 ml = 1 g" assumption). v2.0 has 638 entries in 20 groups — [FAO Density DB v2.0](https://www.fao.org/4/ap815e/ap815e.pdf) **[snippet]**. Non-commercial (see Q1).
- **uFiSh**: fish and shellfish in raw, cooked and processed form — [FAO/INFOODS](https://www.fao.org/infoods/infoods/tables-and-databases/faoinfoods-databases/en/) **[snippet]**.

### Inferences
- For fdiet's CSV-in-git model:
  - Fineli (CSV) drops in most easily.
  - CIQUAL and BLS need a one-off XLSX→CSV conversion, which the licences allow; CC BY only requires marking the change.
  - Livsmedelsdatabasen needs an API crawl script.
  - FDC's CSVs are relational (food, nutrient, food_nutrient, food_portion) and need a join.
- **Household measures**: Fineli is the only open European table *confirmed* to ship portion and household-measure weights. FDC SR Legacy / Foundation ship a `food_portion` table; this is from my knowledge and was not verified this session. Those could complement `ref_food_measures`, but the measures are in US units (cup, tbsp) and Finnish portions.
- **Raw/cooked**: all these tables follow BEDCA's style of separate rows per preparation (for example "Pollo, pechuga, plancha"). Cooked states are encoded in names, not as a separate field, so fdiet's `FoodState` name-reading approach transfers.

### Gaps
- Whether CIQUAL 2025, BLS 4.0 and CoFID carry an edible-portion (refuse) factor column. Not stated in the pages I could read. BEDCA's `edible_portion` is used by `PortionScaler`, so this matters.
- Whether BLS 4.0 file contains English names.
- FDC `food_portion` contents and nutrient counts were not verified this session.
- The Fineli column list (EDPORT, food units) was not verified; the official page returned 403.

## Q3. Spanish-language (Latin American) tables: licences and usefulness

### Takeaway
The Latin American tables have Spanish names but are mostly old PDFs with no stated licence: Mexico 2015, Chile 1990, Costa Rica 2006–2013, INCAP 2007/2018 print. Their names are Latin American Spanish ("palta", "porotos"). None is a safe, machine-readable, licensed source. ArgenFoods (2010, Excel) is the only bulk-usable one, and its licence is unknown.

### Cited Findings
- FAO/INFOODS lists ([FAO INFOODS Latin America](https://www.fao.org/infoods/infoods/tables-and-databases/latin-america/en/)):
  - Mexico: INCMNSZ "Tabla de Composición de Alimentos y Productos Alimenticios Mexicanos", 2015, PDF — https://www.incmnsz.mx/2019/TABLAS_ALIMENTOS.pdf
  - Chile: Universidad de Chile, 8th ed. 1990, PDF.
  - Argentina: ArgenFoods Compilation 2010, PDF and Excel — http://www.unlu.edu.ar/~argenfoods/Tablas/Tabla.htm
  - Costa Rica: INCIENSA 2006–2013, PDFs.
  - INCAP Central America: 2nd ed. 2007 PDF; 3rd ed. 2018 printed only.
  - LATINFOODS: online database at http://latinfoods.inta.cl/composicion-de-alimentos/
  - FAO notes no licence for any of these.
- Argentina's official table is SARA 2 (Ministerio de Salud, 2022), which includes ArgenFoods data — [ENNyS 2 / SARA 2 PDF](https://iah.msal.gov.ar/doc/720.pdf) **[snippet]**.

### Inferences
- Without a stated licence, the default is "all rights reserved", which is worse than BEDCA's explicit non-commercial grant. Do not ingest these.
- Spanish names alone do not make them more useful than CIQUAL or BLS. Translating a few hundred generic food names is a small one-off job compared with licence risk, and Latin American names diverge from Peninsular Spanish anyway.

### Gaps
- No licence terms found for any Latin American table. LATINFOODS portal terms were not fetched.
- Whether SARA 2 is published as a dataset with open terms.

## Q4. Is there a newer or open BEDCA?

### Takeaway
No. BEDCA is still about 950 foods under non-commercial terms. A public datos.gob.es request for an open structured release (April 2026) has had no documented AESAN response. AESAN's only open food dataset is a branded 2022 product table.

### Cited Findings
- 950 foods and 48 components. The BEDCA group was "reviewing the structure and food composition data" under a contract "until 2025" — [El Aderezo](https://www.eladerezo.com/salud-y-bienestar/base-de-datos-espanola-de-composicion-de-alimentos.html) **[secondary snippet]**.
- The datos.gob.es request of 2026-04-14 is "Assigned", last updated 2026-09-29, with no answer. It mentions an unofficial GitHub CSV distributed under BEDCA's non-commercial terms (this is fdiet's current source, TerjeRu/bedca-database) — [datos.gob.es](https://datos.gob.es/en/solicitud-de-datos/base-de-datos-bedca).
- AESAN open data: "Base de datos de composición de alimentos y bebidas comercializados en España 2022" is Excel and branded. AESAN disclaims accuracy, and it lists NUTRIFEN, TABULA and BADALI as other Spanish databases — [AESAN datos abiertos](https://www.aesan.gob.es/en/datos-abiertos/alimentos-y-bebidas).

### Inferences
- The AESAN 2022 branded dataset could complement `food_items` (fooddata.csv) rather than BEDCA. Its reuse licence is not stated on the page; check datos.gob.es, whose default is usually CC BY 4.0 (unverified).

### Gaps
- Whether a BEDCA v2 was released after the 2025 contract. No source found.
- The licence of the AESAN 2022 branded dataset.

## Q5. How easily do rows map to generic Spanish foods ("lechuga", "pollo, pechuga, plancha", "pan de molde")?

### Takeaway
Ranking for fdiet:

1. **CIQUAL 2025**: Mediterranean-adjacent diet, French and English names, about 3,500 foods including breads and cooked meats.
2. **BLS 4.0**: largest at 7,140 foods with dishes, but German names and Central European foods.
3. **Fineli / Livsmedelsdatabasen / Matvaretabellen**: English (or English-available) names, but Nordic food choices.
4. **CoFID**: English, UK foods.
5. **USDA SR Legacy / Foundation**: English, very detailed preparation states, US foods.

None has Spanish names, so a curated translation and mapping table (`bedca_id` ↔ `ciqual_code`, plus Spanish name) is needed whatever the choice.

### Cited Findings
- CIQUAL covers "the most consumed foods in France", with 3,484 foods, an English version, and 2025 additions of breads, cereals, processed meats and tacos — [ANSES](https://ciqual.anses.fr/cms/en/2025-anses-ciqual-table), [Zenodo](https://zenodo.org/records/17550133).
- BLS 4.0 has "more than 7,000 foods and dishes" — [heise.de](https://www.heise.de/en/news/Bundeslebensmittelschluessel-4-0-Nutrient-database-now-publicly-available-11123877.html) **[snippet]**.
- Livsmedelsdatabasen provides English names plus FoodEx2 and LanguaL codes — [Livsmedelsverket](https://www.livsmedelsverket.se/en/about-us/open-data/food-composition-data/).
- BEDCA is also coded in LanguaL and FoodEx2 — [El Aderezo](https://www.eladerezo.com/salud-y-bienestar/base-de-datos-espanola-de-composicion-de-alimentos.html) **[secondary]**.

### Inferences
Mapping ease per target food:

| Source | "lechuga" | "pollo pechuga plancha" | "pan de molde" | Notes |
|---|---|---|---|---|
| CIQUAL 2025 | Easy (laitue, crue) | Good (poulet, filet, grillé / poêlé likely) | Good (pain de mie) | Best overall; French→Spanish names are close |
| BLS 4.0 | Easy (Kopfsalat) | Good, many cooked meat states | Good (Toastbrot) | Most dishes; German names |
| Fineli | Easy | Moderate | Moderate (Nordic breads) | Only one confirmed with household measures |
| Livsmedelsdatabasen | Easy | Moderate | Moderate | API only |
| CoFID | Easy | Good | Good (white sliced bread) | UK products |
| USDA SR / Foundation | Easy | Good (very granular) | Moderate (US bread) | CC0, but US formulations |

(The food-name examples in this table are illustrative and not verified row by row.)

- **FoodEx2 / LanguaL codes are a shared key**: BEDCA, Livsmedelsdatabasen and likely CIQUAL and BLS carry them, so a semi-automatic BEDCA→other-table crosswalk is feasible. Matching should stay "offered, person decides", the same as fdiet's `NameMatcher` philosophy.
- **Data-quality caveat:** swapping BEDCA's Spanish-analysed figures for French or German ones changes values for Spanish-specific products (jamón serrano, chorizo, regional breads). A pragmatic path:
  - keep BEDCA for non-commercial use;
  - add CIQUAL as an open fallback table with its own attribution;
  - plan to switch the default source if fdiet goes commercial.
- **Licence safety for a project that may go commercial**:
  - **Safe**: CIQUAL 2025 (CC BY 4.0 / Etalab 2.0), BLS 4.0 (CC BY 4.0), Fineli (CC BY 4.0), Livsmedelsdatabasen (CC BY 4.0), Matvaretabellen (NLOD 2.0), CoFID (OGL v3), USDA FDC (CC0), EFSA micronutrient DB (CC BY 4.0).
  - **Not safe**: BEDCA (non-commercial), FAO/INFOODS (CC BY-NC-SA 3.0 IGO), Italian BDA (paid, research-only), Latin American tables (no licence).
  - **Unclear, ask first**: NEVO (agreement plus no-modification clause), Frida (attribution confirmed, commercial use unverified), CREA (free to consult; commercial use "requested to cite", but no bulk file or formal licence), INSA TCA (free access, no named licence), Open Food Facts (open but ODbL share-alike).

### Gaps
- No row-level mapping test was done. The quality of a CIQUAL or BLS match for each of example-ui.xlsx's 210 ingredients is unmeasured; a mapping spike would answer it.
- Whether CIQUAL and BLS files actually contain FoodEx2 codes was not verified.
