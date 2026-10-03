# FD-033 phase A — composition spike: CIQUAL 2025 and BLS 4.0

Date: 2026-10-02 · Story: `product/stories/FD-033-open-data-only.md` · Type: spike (no production code,
no migration, no database writes).

**User decisions going in:** BEDCA is replaced by **both** CIQUAL 2025 and BLS 4.0. Existing matches
are reset rather than migrated. BEDCA keeps running until the replacement is live.

Working files (downloads and helper scripts) are in `C:/Users/victo/.claude/jobs/7ca339de/tmp/fd033-spike/`,
outside the repo: `search.js` (name search over both tables), `coverage.js` (example-ui.xlsx),
`coverage_bedca.js` (BEDCA foods in use), `db.js` (read-only queries), `load.js`, `dump.js`.

## 1. Answer in brief

- **Coverage is not the problem.** For the 210 ingredients of example-ui.xlsx ("Dieta 1"), CIQUAL has a
  same-food, same-state equivalent for **93 %** and BLS for **84 %**. Combined: **95 %** with a usable
  proxy for the rest, and **0** with no equivalent. Of the 80 BEDCA foods fdiet references today, 77 have
  a same-food equivalent in at least one of the two.
- **Neither file publishes an edible-portion factor.** Both give values per 100 g of the edible part
  and nothing else. Without a factor, a gross weight cannot be converted, so the 16 `GROSS` rows in
  `aesan-mec-2010/rations.csv` stop counting. This is the one hard blocker, and it has an open fix
  (section 6).
- **Neither file carries FoodEx2 or LanguaL codes**, so BEDCA's codes cannot bridge to them. Every link
  is made by name and reviewed by a person.
- **Recommendation:** one table with a `source` column. CIQUAL answers first, BLS fills the gaps. Spanish
  names go in a fdiet-owned crosswalk under CC BY 4.0, written in BEDCA's head-first style
  (`Pollo, pechuga, plancha`). With that style, `NameMatcher`, `FoodCategoriser` and the `FoodState`
  reader keep working with few changes.

## 2. Downloads (retrieved 2026-10-02)

### CIQUAL 2025 (ANSES)

- Record: <https://zenodo.org/records/17550133>, DOI `10.5281/zenodo.17550133` (concept DOI
  `10.5281/zenodo.17550132`), "Ciqual French food composition table 2025", published 2025-11-19,
  data dated 2025-11-03. Zenodo licence field: `cc-by-4.0`. The research note also cites Etalab
  Licence Ouverte 2.0 (Recherche Data Gouv copy, doi:10.57745/RDMHWY), but the Zenodo record
  itself lists only CC BY 4.0. No separate licence or attribution file ships with the data.
  Creator: "Ciqual". Contact: ciqual@anses.fr.
- File URL pattern: `https://zenodo.org/api/records/17550133/files/<name>/content`. Each MD5 below
  matches the checksum Zenodo publishes.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Table Ciqual 2025_ENG_2025_11_03.xlsx` | 1,537,602 | `2c3495c8136d17356c50db410918da2102cb28096cb497c9cdf83c5f8ecb10ba` |
| `alim_2025_11_03.xml` (foods, FR + EN names) | 1,581,031 | `e0b1de25b3039028205e9d54a96892e403e1b313c2efeb41180fabe132627478` |
| `alim_grp_2025_11_03.xml` (group names FR + EN) | 80,421 | `e216928be1001aed15ba1b120405b70a98145da2a1a76026d6e33542bd5e39dc` |
| `const_2025_11_03.xml` (components) | 17,955 | `3a231ea3e9836e4ac0b6ff94511e010210b2523d1b8b486c6b29e211066ba3f3` |
| `Table Ciqual 2025 doc ENG_2025_11_19.pdf` | 4,197,666 | `dd6325a83dd13177abf156626e1a94ba8623fd04767719072257dbb53cadec6d` |

Three files were not downloaded: `compo_2025_11_03.xml` (69 MB, the same values as the XLSX plus
confidence codes), `sources_2025_11_03.xml`, and the `.xls` copy. The PDF guide is image-only
(no text layer), and no tool here could render it, so it was **not read**.

Row counts: 3,484 foods. The XLSX has 84 columns: 9 for identity, 74 components and the Jones
factor. A second sheet, "INFOODS codes", has 74 rows.

### BLS 4.0 (Max Rubner-Institut)

- Page: <https://blsdb.de/download>. The file is linked as
  `https://blsdb.de/assets/uploads/BLS_4_0_2025_DE.zip?token=…`. The token is issued by the page and
  rotates, so a manifest should record the page, the DOI and the hash, not the link.
- Licence text on the page (verbatim): *"Die Daten des Bundeslebensmittelschlüssels (BLS) stehen als
  Open Data zur freien Verfügung. Die Nutzung ist unter der Lizenz CC BY 4.0 (Creative Commons
  Namensnennung 4.0 International) gestattet. Bei Verwendung ist das Max Rubner-Institut als
  Herausgeber zu nennen. Zitierweise: Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS),
  Version 4.0 — Deutsche Nährstoffdatenbank. Karlsruhe. DOI: 10.25826/Data20251217-134202-0"*
- Documentation §9.3 says the data is provided "kostenfrei und ohne Lizenzbarrieren", explicitly
  including app and software development. Version 4.0, "Stand: Dezember 2025".

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `BLS_4_0_2025_DE.zip` | 14,263,306 | `12b7a6ba62807ec9b301eb276f897dc85f99b2292311618dec3749a12d984c91` |
| ↳ `BLS_4_0_Daten_2025_DE.xlsx` | 14,093,078 | `524bbefe25b691f5cb3de7a9f3e27fa2967aebfeabf217d99414ba7806e78c60` |
| ↳ `BLS_4_0_Components_DE_EN.xlsx` | 21,741 | `359aefcd2086f45e62ff3dbd0c8536306e594a5806ac325d8bf86f484561bdf4` |
| ↳ `BLS_4_0_Dokumentation_DE.pdf` | 469,803 | `6d83913f9b705399f86795a9c3afcb2d0454d1129bfb24ce53da911c5a6a24b6` |

Row counts: 7,140 foods and 138 components. The data sheet has 418 columns: code, the German name,
the English name, three columns per component (value, data origin, reference), and a `Hinweis`
column, which only 4 rows fill.

## 3. What the files contain

| | CIQUAL 2025 | BLS 4.0 |
| --- | --- | --- |
| Names | French (`alim_nom_fr`, XML only) + English (`alim_nom_eng`, XLSX and XML); scientific name for 720 foods | German + English for all 7,140 |
| Food groups | 3-level code (`alim_grp` / `ssgrp` / `ssssgrp`), FR + EN names, 11 top groups | leading letter of the code, 20 letters (B bread, C cereals, F fruit, G vegetables, T fish, U meat, V poultry, W sausages, X/Y dishes = 2,050 rows, …) |
| FoodEx2 / LanguaL | **none** | **none** |
| Component codes | INFOODS tags (sheet "INFOODS codes", `const` XML) | EuroFIR/INFOODS-style codes in every header (`ENERCJ`, `PROT625`, `FASAT`, …) |
| Basis | per 100 g edible part (no factor column) | "100 g essbaren Anteil; nicht essbarer Anteil bereits herausgerechnet" (doc §7.2); no factor column |
| Edible-portion factor | **absent** | **absent** |
| Raw / cooked state | in the English name: `raw`, `cooked`, `boiled/cooked in water`, `grilled/pan-fried`, `roasted/baked`, `steamed`, `braised`, `canned, drained`, `dried` | in the English name (`raw`, `boiled`, `stewed`, `baked`, `grilled`, `fried without fat (pan)`, `deep-fried`, `canned, drained`, `dried`). For single foods the last two code digits usually agree: `00` raw, `32` boiled, `52` stewed, `62` baked, `72` grilled, `82` pan-fried, `92` deep-fried, `02` canned. This does not hold for the X/Y dishes. |
| Missing / qualified values | `-` missing, `traces`, `< 0,2` (below a limit); **decimal comma**; XML entities in names (`&apos;`) | `-` missing, `TR`, `<LOD`, `<LOQ`, `<LOD or <LOQ`; numbers otherwise |
| Provenance per value | confidence code (in `compo` XML, not inspected) | "Datenherkunft" per value: about 5,500 of 7,140 rows come from **recipe calculation**, and analysed values are a minority |

### fdiet's 14 components

| `Nutrient` | CIQUAL column (unit) | filled / 3,484 | BLS code (unit) | filled / 7,140 |
| --- | --- | --- | --- | --- |
| ENERGY | Energy, Reg. EU 1169/2011 (kJ **and** kcal) | 3,341 (143 `-`) | `ENERCJ` kJ, `ENERCC` kcal | 7,140 (all formula) |
| PROTEIN | Protein (g), N × Jones factor; crude N × 6.25 also given | 3,455 | `PROT625` g (N × 6.25) | 7,140 |
| FAT | Fat (g) | 3,464 | `FAT` g | 7,137 |
| SATURATED_FAT | FA saturated (g) | 3,236 | `FASAT` g | 7,117 |
| CARBOHYDRATES | Carbohydrate (g) | 3,414 | `CHO` g (available) | 7,140 |
| SUGARS | Sugars (g) | 3,261 | `SUGAR` g | 7,140 |
| FIBER | Fibres (g) | 3,414 | `FIBT` g | 7,126 |
| WATER | Water (g) | 2,939 | `WATER` g | 7,134 |
| SODIUM | Sodium (mg) | 3,082 | `NA` mg | 7,114 |
| POTASSIUM | Potassium (mg) | 2,617 | `K` mg | 7,124 |
| CALCIUM | Calcium (mg) | 2,700 | `CA` mg | 7,126 |
| IRON | Iron (mg) | 2,653 | `FE` mg | 7,118 |
| CHOLESTEROL | Cholesterol (mg) | 2,560 | `CHORL` mg | 7,115 |
| VITAMIN_C | Vitamin C (mg) | 2,398 | `VITC` mg | 6,976 |

"Filled" counts every cell that is not `-`, including `traces` and `<` cells. Both tables publish all 14
components in the units fdiet already handles (kJ, kcal, g, mg), and every food row uses one unit
per component, so no unit varies by row the way BEDCA's does. The methods differ, though: CIQUAL
protein uses Jones factors while BLS and BEDCA use 6.25, and BLS's energy is always its own
formula. A food's figures should therefore always be shown with its source.

## 4. Coverage test

### Method

- **(a) example-ui.xlsx, sheet "Dieta 1".** `coverage.js` splits each cell on `+` outside brackets and
  keeps the text after a top-level `Dish:` heading, as `MealTextParser` does. That gives **210**
  ingredients, the figure CLAUDE.md quotes. Each ingredient is assigned to its **head food** (the
  food word that appears first, so `2 tostadas integrales con aguacate` is the bread), which gives
  **81 distinct foods**.
- **(b) BEDCA foods in use.** A read-only query (`SET SESSION TRANSACTION READ ONLY`, SELECTs only) found
  the distinct `bedca_food_id` in `recipe_ingredients` (36 foods, 157 rows), `extra_foods` (0),
  `ref_food_measures` (57) and `ref_rations` (60). Together these are **80 foods**. The 60 ids in the
  reference CSVs are all among them. Other dev-DB counts: 958 ingredients in 8 diets, 157 matched to
  BEDCA, 1 to a branded item.
- **Judging.** For every food I searched the English names of both tables, plus the French and German
  names, with regexes (`search.js`, shortest names first) and chose the candidate by hand.
  **Y** = the same food in the same state. **P** = a usable proxy: the same food in another state or
  variety, or the nearest analogue. **N** = nothing defensible. The ids chosen are in the scripts.
  One reviewer judged everything; a nutritionist has **not** validated it. This is availability
  of an equivalent, **not** an outright name match (see the caveat below).

### (a) example-ui.xlsx — 210 ingredients, 81 foods

| | Y | P | N |
| --- | --- | --- | --- |
| CIQUAL, by ingredient | 196 (93.3 %) | 14 (6.7 %) | 0 |
| BLS, by ingredient | 177 (84.3 %) | 28 (13.3 %) | 5 (2.4 %) |
| **Combined, by ingredient** | **199 (94.8 %)** | 11 (5.2 %) | **0** |
| CIQUAL, by food | 73 | 8 | 0 |
| BLS, by food | 63 | 13 | 5 |
| Combined, by food | 76 | 5 | 0 |

- **P in both tables:** queso fresco de Burgos (CIQUAL faisselle / fromage frais, BLS cottage cheese or
  quark), bacalao **desalado** (both have fresh cod only), merluza a la plancha (CIQUAL braised, BLS
  stewed or raw), hummus casero, and salsa de soja **baja en sodio**.
- **Only CIQUAL has:** herbs and spices (menta, orégano, tomillo, hierbas provenzales, pimentón). BLS 4.0
  has no herb or spice rows. CIQUAL also has `Jamón sec Serrano` (BLS: Parma), cherry tomato,
  red onion, canned sweetcorn, turkey slices and café con leche.
- **Only BLS has:** grilled pork tenderloin, baked sweet potato, homemade chicken stock and guacamole
  (as recipes), and grilled or boiled variants of nearly every meat, fish and vegetable.

### (b) BEDCA foods in use — 80 foods, 157 matched ingredient rows

| | Y | P | N |
| --- | --- | --- | --- |
| CIQUAL, by food | 75 | 4 | 1 |
| BLS, by food | 73 | 6 | 1 |
| **Combined, by food** | **77** | 2 | 1 |
| Combined, by `recipe_ingredients` row (157) | 153 | 2 | 2 |

- Not Y in either table: *Néctar de ciruela* (N in both), *Piña, enlatada en su jugo* (both only have
  pineapple in syrup), *Queso fresco de burgos* (P).
- Only one table is Y: Acelga (BLS leaf; CIQUAL stalk only), Soja germinada en conserva (BLS),
  Calabaza / Coliflor congelada / Guisante congelado / Jamón serrano (CIQUAL).

### Caveat on the 41/210 acceptance criterion

Today 41 of the 210 ingredients match outright because BEDCA's Spanish name equals the text. Neither
new table has Spanish names, so outright matches will come **only from the crosswalk**: its Spanish
names and aliases (`lechuga`, `tomate`, `AOVE`, `pan integral`). Coverage of 95 % is the ceiling the
crosswalk can reach. If it covers these 81 foods with the words the diets use, outright matches can
pass 41. Without it, outright matches are 0.

## 5. Recommendation: how to combine the two

1. **One table with a `source` column, not two.** For example, `composition_foods (id PK, source
   ENUM('CIQUAL','BLS'), source_code VARCHAR, name_original, name_en, group_code, 14 × value + unit,
   …)` with `uk (source, source_code)`. Every consumer (`recipe_ingredients`, `extra_foods`,
   `ref_rations`, `ref_food_measures`, the reference CSVs) then points at one id column. Two tables
   would double every foreign key and add a second "at most one of these is set" rule next to the
   existing BEDCA / branded pair. The source codes are stored as published: CIQUAL `alim_code` is
   numeric, BLS is `C131000`.
2. **CIQUAL answers first, and BLS fills the gaps.** CIQUAL is closer to the Mediterranean diet,
   contains herbs, spices and Serrano ham, and is mostly analysed data. BLS is broader and nearly
   complete, but mostly calculated and centred on German dishes. When one Spanish name maps to both
   tables, the crosswalk marks one entry as preferred, CIQUAL by default. BLS is preferred where only
   it has the state written (for example `plancha`, `asado` for meats). Exact matching resolves
   Spanish name → preferred food. Suggestions rank all crosswalk names, with the English name as a
   fallback for foods that have no Spanish name yet.
3. **Attach Spanish names through a curated crosswalk** (`reference-data/composition-es/links.csv` or
   similar): `source, source_code, name_es, aliases, preferred, edible_portion,
   edible_portion_source, note`. It is fdiet's own work under CC BY 4.0 with a "changes made" note.
   Start with the ~120 foods above (81 + 80, overlapping), then grow it on demand. The machine can
   pre-fill candidates by matching BEDCA's own `f_eng_name` against both tables' English names with
   `NameMatcher`, but a person approves every row. The crosswalk must hold **no BEDCA values**:
   Spanish words are fdiet's, and any bridge keyed on BEDCA ids lives only in the phase-D report,
   which phase E removes.
4. **Write `name_es` in BEDCA's head-first style** (`Pollo, pechuga, plancha`, `Bacalao, horno`).
   `FoodCategoriser` (head word wins), the `FoodState` reader and `NameMatcher` were all tuned to
   that shape. Feeding them curated Spanish names in the same shape keeps them working and keeps
   categories derived on read. The source group codes (CIQUAL `alim_ssgrp`, the BLS letter) are
   useful as a cross-check in a test, not as the category. For foods without a Spanish name, a small
   English state-word table (`raw`, `boiled`, `grilled`, `canned, drained`, …) gives the state, and
   the categoriser leaves them uncategorised, as it does now for 1 of 957.
5. **Existing matches are reset** (user decision). The before/after report in phase D still needs to
   count how many matched rows became unmatched in each diet.

## 6. Blockers and risks

1. **No edible-portion factor (blocker for gross weights).** Today 16 `GROSS` ration rows
   (`aesan-mec-2010`) and every gross household measure depend on BEDCA's `edible_portion`, which
   BEDCA fills for all 957 foods. Under "blank beats wrong" they would weigh nothing. **Fix:** an
   `edible_portion` column in the crosswalk, filled from the USDA FoodData Central SR Legacy "refuse"
   percentage (CC0) with its source cited on each row. When it is null, the gross measure is
   refused, as now.
2. **No FoodEx2 / LanguaL** in either file. BEDCA's `langual` / `foodexcode` cannot bridge, so all
   mapping is by name with a person reviewing it. CIQUAL's scientific name (720 foods) is a weak
   extra key.
3. **Energy can be missing in CIQUAL** (143 foods are `-`). `AlternativeService` assumes that energy
   is "the one figure every row publishes" (true for BEDCA). It must handle a null energy, or BLS must
   supply it for that food.
4. **Qualified values.** `traces`, `< 0,2`, `TR`, `<LOD` and `<LOQ` are not numbers. Storing values as
   published needs a decision in B: a nullable value plus a qualifier column per value, or null with
   the raw string kept. `NutrientValue` is value + unit today. CIQUAL also uses a decimal comma.
5. **Mixed methods**: protein (Jones factor vs 6.25), energy formula, and BLS's recipe-calculated
   majority. Totals for a diet that mixes sources are still sums, but each food must show its source.
   The attribution footer must name both sources (and BEDCA, until E).
6. **Spanish specifics** are weak in both: Burgos cheese, desalted cod, low-sodium soy sauce, plum
   nectar, pineapple in juice. These remain proxies or stay unmatched, and the nutritionist decides.
7. **BLS download link** uses a rotating token, so FD-034 should record the page, the DOI and the
   SHA-256.
8. **Licence wording for CIQUAL.** Zenodo states CC BY 4.0 only, and the Etalab dual licence is on
   the Recherche Data Gouv copy. Either one allows commercial use with attribution. Record which
   copy was taken (Zenodo, here).

## 7. Not verified

- The CIQUAL PDF guide was not read (image-only), so CIQUAL's own statement on the edible-part basis
  is inferred from the data and the absence of a factor column. CIQUAL's `compo` XML (confidence
  codes) was not downloaded.
- One person judged every verdict, and a nutritionist has not reviewed them. Some calls are arguable,
  such as a reconstituted stock cube for *caldo vegetal* and faisselle for *queso de Burgos*.
- The head-food assignment approximates `MealTextParser`. It reproduces the 210 count, but it is not
  the parser itself.
- The USDA refuse fix in §6.1 was not checked against the 120 foods.
