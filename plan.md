# Plan — rations, portions, household measures and exchanges for the diet creator

Status: **phases 1–3 implemented, plus the clinical carbohydrate count** (2026-09-13), with the
decisions in §10 answered (see below). Phase 0 (permission emails) is not something code can do,
and phases 4–5 are open.
Research date: 2026-09-13. Every figure below says where it came from; a figure marked
*(verify)* was read through a text extraction or a secondary page and has to be checked
against the original page before it is seeded.

> **Historical note (2026-10-04, FD-033 phase E).** BEDCA has been removed from fdiet: its table,
> columns, endpoint, classes and data files are gone (migration `V22`), and the composition figures
> come from CIQUAL 2025 and BLS 4.0 (CC BY 4.0) through fdiet's Spanish crosswalk. **Every BEDCA
> mention below is historical** — it describes the plan as written, when BEDCA was the composition
> database — and is kept unedited as the record of the research. See `CLAUDE.md` and
> `product/stories/FD-033-open-data-only.md`.

---

## Implementation status

**Answers to §10.** Q1 fdiet stays open source (BEDCA stays non-commercial regardless). Q2 AESAN 2022
is the default adult profile. Q3 a nutritionist may override a measure's weight per diet. Q4 the adult
meal distribution is borrowed from the AESAN/MEC 2010 school document, labelled as such. Q5 clinical
exchanges sit behind a `clinical` flag on the diet. Q6 patients may carry birth date and sex.

**Done.**
- Backend: the `reference` context (V8), patient birth date/sex and diet profile/clinical (V9),
  ingredient state/size/measure (V10); seeded AESAN 2022, AESAN/MEC 2010 (four bands + meal shares),
  5 al día 2019 (80 rations, 62 measures) and the 10 g HC ration definition. Measure-first parser,
  state and size words, measure-aware weighing, `countedByMeasure`, state-mismatch flag, the
  resolver's attach rule, per-diet measure criteria, `compose`, and `GET /api/diets/{id}/rations`.
- UI: *Nueva dieta* (blank or Excel) with the profile proposed by age and a clinical toggle; a
  reference chip and clinical toggle in the editor header; template rows and all seven days for a new
  week; *Añadir por raciones* in the dish panel; measure candidates and *Criterio para esta dieta* in
  the link drawer; measure and state-mismatch chips on ingredients; the rations strip (daily and
  weekly checks, groups, meal energy with the borrowed label, HC rations when clinical, uncounted
  reasons); attribution footer; birth date and sex in the patient form.
- Re-importing `example-ui.xlsx`: 41 of 210 ingredients match outright (32 before), and fruit
  written as `1 kiwi` or `1 naranja` is weighed through 5 al día's unit weights with no per-diet
  criterion. The 21 `cdta AOVE` stay unweighed until a criterion is given, as intended.

**Changed from this plan.**
- No `ref_household_measures`, `ref_group_mappings` or `ref_yield_factors` tables. The measure
  vocabulary is the `HouseholdMeasure` enum; group mapping is `food_category` + `keywords` on each
  row; yield factors are Phase 4.
- LanguaL is not used: its raw/cooked codes proved inconsistent (a raw tongue coded as baked), so
  state is read from BEDCA names.
- Oil stays at 1 g/ml (≈9 % high) so `10 ml` and `1 cucharada sopera` never disagree.
- A recommendation with anything uncounted that might belong to a group reads `UNCERTAIN` rather
  than `BELOW`.
- RD 315/2025 frequencies are not seeded.

**Done 2026-09-28 (V11, V12).**
- Ranges in the parser: `2-3 nueces`, `(40-60 gr)`, `1 a 2 cdta` keep both ends
  (`diet_ingredients.quantity_max`); a range weighs nothing until the nutritionist fixes a value in
  the link drawer (PATCH `quantity`). A publish sends the range back, so it survives.
- Journal extras are measure-aware (`extra_foods.food_measure_id`, same choosing rule and the same
  `IPortionScaler` door as the week); the extra panel offers the measures that weigh the unit.
- The rations strip (and the attribution footer, which was missing there) is on the patient screen.
- Phase 4: general 10 g exchanges (HC, protein, fat; Russolillo & Marques-Lopes 2011, the definition
  only) counted per day, meal and dish, behind an *Intercambios* toggle in the strip and the dish
  panel; the clinical HC ration still only on clinical diets. Alternatives take `basis`
  (energy/HC/protein/fat) and `profile` (≈ N raciones, fuente). 29 USDA 2014 cooking yields for meat
  and poultry (`ref_yield_factors`), offered beside a raw/cooked mismatch and never applied.

**Not done yet.**
- A bracket state that contradicts the name (`pechuga a la plancha (150 g en crudo)`) still reads as
  no state, so the mismatch and the yield hint only fire when the name itself carries no cooking word.
- Yields exist for meat and poultry only; rice, pasta and legumes (the ~3× case) need Bognár/EuroFIR
  or FAO permission. The ration count does not use yields either.
- No alternatives screen in the UI; basis and profile are API-only.
- Russolillo exchange lists, SENC, DIAL and FINUT figures wait on permission (Phase 0).
- Phase 5 (special populations): needs the ASPCAT 2022 and AEP 2018 documents read for seedable values.

---

## 0. What this is for

The diet creator today is a text editor over a week grid: the nutritionist types
`lechuga (80 gr) + tomate (100 gr)`, the backend reads it, matches each food to BEDCA, and
works out the figures. It is good at *writing* a diet and poor at *building* one:

- Nothing helps the nutritionist decide **how much**. Every gram is typed from memory.
- Anything written in a household measure is **uncounted**. `PortionScaler` knows g, kg, mg,
  ml, cc and l and nothing else, by design (`src/main/java/com/fdiet/diet/helpers/PortionScaler.java`).
  In `example-ui.xlsx`, roughly **67 of ~319** `+`-separated fragments carry no gram or
  millilitre figure (a rough regex count that includes a few header cells): `1 cdta AOVE`
  (the most repeated one, and each is ~40 kcal nobody counts), `1 kiwi`, `1 huevo cocido`,
  `2 lonchas de pavo`, `1 diente de ajo`, `1 pera pequeña`.
- The **state** of a food is written and then thrown away: `arroz blanco (70 g crudo)`,
  `lentejas cocidas (180 gr)`, `garbanzos (100 g cocidos sin piel)`. About 31 fragments carry a
  state word. `MealTextParser` keeps the number and the unit and drops the word, so a raw-rice
  weight can end up priced against boiled rice, or the other way round, a ~3× error.
- There is **no create or import screen** in the UI. The backend has `POST /api/diets` and
  `POST /api/diets/import`, but `UI/src/api/diets.ts` exposes neither, and the empty-state
  message in `stores/dietDraft.ts` tells the nutritionist to import from Excel even though the
  UI can't.

The goal: **a nutritionist builds a week from standard rations and household measures, and
every gram that comes out of them says which table it came from.** It is not a diet
generator. The machine offers quantities with their provenance; the nutritionist picks.
That is the same rule the codebase already follows for food matching: *the machine offers and
the nutritionist decides; a blank is better than a wrong number.*

---

## 1. The concepts, kept apart

The research brief is right that these are different things. The data model in §6 gives each
one its own table.

| Concept | Question it answers | Example | Unit of the answer |
| --- | --- | --- | --- |
| **Food composition** | What does 100 g of this food contain? | `Arroz blanco, crudo`: energy, protein… per 100 g edible portion | per 100 g |
| **Portion** | How much of it do people normally eat, or how much does a piece weigh? | 1 kiwi ≈ 80 g net (100 g gross) | grams of one food, observed or typical |
| **Ration (ración)** | What quantity does a guideline define as one standard serving? | Rice: 60–80 g dry (AESAN 2022) | grams/ml of a food or group, *defined* |
| **Household measure** | How is a quantity said in the kitchen? | 1 cucharada sopera of oil = 10 ml (AESAN) *or* 15 ml (SENC) | a measure → grams for a given food |
| **Equivalence** | What can replace this under a stated criterion? | 40 g bread ≈ 100 g potato ≈ 30 g dry pasta ≈ 100 kcal | pairs/lists + the criterion |
| **Exchange (intercambio)** | How many standard nutrient units does this quantity carry? | 1 carbohydrate exchange = 10 g carbohydrate | a count of defined units |
| **Recommendation** | How often, or how many rations, for whom? | Legumes ≥4 rations/week, adults (AESAN 2022) | rations per day/week/meal + population |

How they relate:

- A **ration** is usually *stated as* a household measure (`1 plato hondo normal`) and a weight
  range. The weight is the definition; the measure is how it's explained.
- A **recommendation** counts **rations**. It means nothing without that source's own ration
  definition. `3 rations of dairy/day` under AESAN 2022 is a different amount of food from the
  same sentence under SENC 2018, so a recommendation row must point at its source's ration rows.
- An **exchange** is a ration whose size is fixed by nutrient content (10 g carbohydrate), not by
  what a plate looks like. So an exchange list is really a per-food table of grams per unit of a
  nutrient, and it can be **derived from composition data** (BEDCA). That matters for licensing
  (§5).
- An **equivalence** is the relation that exchanges and rations make possible: two foods are
  equivalent *with respect to a named criterion*. `alternative/` already ranks equivalence by
  energy and composition inside a category. What this plan adds is the named criterion and the
  source.
- A **portion** (unit weight, piece size) is the bridge that lets `1 kiwi` or `1 huevo` be
  weighed at all. It depends on the food, the size (small/medium/large) and whether the weight is
  gross or edible.

---

## 2. Nutrition Reference Data Catalogue

### 2.1 Licence classes used below

- **A** — clearly reusable for the intended use.
- **B** — reusable with attribution (and whatever other named conditions apply).
- **C** — useful, but permission is required before incorporating the data.
- **D** — reference only; consult, cite, don't load into the database.
- **E** — licence unknown.

*This is a research classification, not legal advice.* Factual values (e.g. "a ration of rice
is 60–80 g dry") are not copyrightable in themselves. A **substantial extraction** of a
compiled table can still fall under the EU database right, and many of these documents carry no
licence at all. Anything C/D/E that we want as more than a cited reference value needs an
explicit permission email (Phase 0, §9).

**Standing constraint:** fdiet already stores BEDCA, which is **non-commercial** without
AESAN's authorisation (`BEDCA-ATTRIBUTION.txt`). As long as that holds, the app is
non-commercial, and several sources below get easier in that frame. If the app ever goes
commercial, the BEDCA authorisation and every C/D source below have to be revisited together.

### 2.2 Tier 1 — Spain

#### R1. SENC — *Guía de la alimentación saludable para atención primaria y colectivos ciudadanos*

| Field | Value |
| --- | --- |
| Institution | Sociedad Española de Nutrición Comunitaria (SENC), with semFYC, SEMERGEN, SEMG, SEPEAP |
| Country | Spain |
| Year / version | 2018 (commercial edition Editorial Planeta, ISBN 978-84-08-20193-9); PDF of chapter 1 + annexes circulates (29 pp.) |
| Direction | J. Aranceta Bartrina, C. Pérez Rodrigo, L. Serra Majem |
| Type | Scientific-society dietary guideline; ration table; recommendation table |
| Categories | 2, 7, 8, 9, 10 (see §2.6 key) |
| Population | General healthy population, stated as "individualizar en situaciones especiales o en presencia de problemas de salud" |
| Age bands (verbatim) | Anexo 1.2: **3-6 años**, **7-12 años**, **etapa juvenil**. Anexo 1.4: **Niños/as**, **Adolescentes**, **Adultos-adultos mayores** (with a # note to adjust for "adultos mayores y ancianos") |
| Rations | Yes: weight + household measure per group, per age band |
| Raw/cooked | Headers say "Peso ración en crudo" / "Peso de la ración (Crudo y neto)"; legumes in the text: "60-80 g/ración en crudo, 150-200 g/ración en cocido" |
| Edible portion | "neto", plus "porción comestible" for fish (children), "con hueso" for meat |
| Recommendations | Yes: frequencies per day/week, "en cada comida principal" grouping, alcohol max by sex |
| Download | PDF (fesnad.org mirror); book |
| Licence | **D** (no licence; commercial edition). Cite values as references; ask SENC before loading the tables (**C**) |
| Recommended role | Primary source for **children and adolescent** ration sizes and for the Spanish household-measure vocabulary; second opinion for adults |
| Limitations | Ranges, not points; group-level, not per food; internal inconsistencies (nuts 25 g in text vs 20–30 g in annex; oil 15 ml adults vs 10 ml children) |
| Official source | https://www.nutricioncomunitaria.org/es/noticia/guia-alimentacion-saludable-ap , PDF https://www.fesnad.org/resources/files/guiaSENC.pdf |

#### R2. AESAN — *Informe del Comité Científico sobre recomendaciones dietéticas sostenibles y recomendaciones de actividad física para la población española*

| Field | Value |
| --- | --- |
| Institution | Agencia Española de Seguridad Alimentaria y Nutrición (AESAN), Comité Científico |
| Country | Spain |
| Year / version | Ref. **AESAN-2022-007**, approved 27 July 2022; *Revista del Comité Científico* nº 36, pp. 11–70 |
| Type | Government scientific report; ration definitions; recommendations |
| Categories | 2, 8, 10, 15 |
| Population | Spanish population; ration sizes are the adult general-population ones (the report has no child ration table) |
| Rations | Yes, per group with examples (pp. 51–53, "Consideraciones generales") |
| Raw/cooked | "en seco" for pasta/rice and legumes; otherwise unspecified |
| Recommendations | Yes, per day/week (p. 50–51 "Conclusiones") |
| Download | PDF (AESAN; mirror at riojasalud.es) |
| Licence | **B** *(verify)*. AESAN's web legal notice authorises total or partial reproduction, modification and distribution, commercial and non-commercial, provided the content is not distorted, the source is cited, and the date of last update is given. The page currently 404s, so re-read it before relying on this. BEDCA's own stricter terms override it for BEDCA data |
| Recommended role | **Default adult ration and frequency reference** (government, newest Spanish adult guideline) |
| Limitations | Adults only; group-level; ranges; oil "10 ml = 1 cucharada sopera" conflicts with SENC |
| Official source | https://www.aesan.gob.es/AECOSAN/web/nutricion/subseccion/recomendaciones_dieteticas.htm |

#### R3. AESAN / Ministerio de Educación — *Documento de consenso sobre la alimentación en los centros educativos*

| Field | Value |
| --- | --- |
| Institution | AESAN (then AESAN/AECOSAN) and Ministerio de Educación, with FESNAD; approved by the Consejo Interterritorial del SNS |
| Country | Spain |
| Year | 2010 |
| Type | Government consensus; school-lunch ration table ("gramajes"); meal energy distribution |
| Categories | 2, 7, 10, 11 |
| Age bands (verbatim) | Anexo II rations: **3-6 años, 7-12 años, 13-15 años, 16-18 años**. Energy table: **3-8, 9-13, 14-18** (IOM bands, by sex). Two different classifications in one document; keep both |
| Raw/cooked / edible | Per row, via footnotes: (1) "peso crudo y neto"; (2) "peso crudo. Medida culinaria estimada con el peso en cocido"; (5) "peso crudo y bruto" |
| Meal context | Distinguishes **plato principal / guarnición / sopa**, so the same food has different rations by role |
| Meal distribution | "25% en el desayuno (o bien, 15% si se trata de un desayuno ligero y 10% en el almuerzo de media mañana), 35% en la comida del mediodía, 10% en la merienda y el 30% restante durante la cena" |
| Download | PDF (https://www.seghnp.org/sites/default/files/2017-05/ACE.pdf; AESAN's own link 404s) |
| Licence | **B** *(verify, same AESAN notice as R2)* |
| Recommended role | Primary **school-age** ration table (it is the only Spanish one found with per-row raw/net/gross flags) and the meal-distribution reference for children |
| Limitations | School lunch only; 2010; partly superseded for frequencies by RD 315/2025 (R4) |

#### R4. Real Decreto 315/2025 (school canteens)

| Field | Value |
| --- | --- |
| Institution | Gobierno de España (BOE-A-2025-7659), develops Ley 17/2011 |
| Year | 15 April 2025; in force 16 April 2026 (second phase April 2027) |
| Type | Regulation: weekly frequencies for 5 school lunches |
| Content | Art. 9 frequencies, e.g. first courses: vegetables 1–2/week, legumes 1–2, rice 1, pasta 1; second courses: fish 1–3, eggs 1–2, meat max 3; fresh fruit dessert 4–5/week *(verify against BOE text)* |
| Portions | No gram portions found in the consolidated text |
| Licence | **A**. Legal and regulatory texts are excluded from intellectual property (art. 13 LPI) |
| Role | Weekly frequency checks for a **school-lunch** diet; does not define rations |

#### R5. Asociación 5 al día — Russolillo G. et al., *Establecimiento del tamaño de raciones de consumo de frutas y hortalizas para su uso en guías alimentarias en el entorno español*

| Field | Value |
| --- | --- |
| Institution | Comité Científico de la Asociación 5 al día (authors incl. Russolillo, Baladia, Moñino, Marques-Lopes, Farran…) |
| Journal | *Rev Esp Nutr Hum Diet* 2019;23(4):205–221, doi:10.14306/renhyd.23.4.628 |
| Type | Peer-reviewed ration proposal **per food**, with household measure, gross and net weight |
| Categories | 2, 3, 4, 5 |
| Method | Adapted USDA serving-size methodology plus the exchange system; survey portion sizes and Spanish market calibres |
| Content | Tabla 4: ration per fruit/vegetable with household measure and net/gross weight; Tabla 5: mean gross and net weight per fruit (e.g. Manzana 200 g gross / 160 g net; Plátano 120/80; Kiwi 100/80; Naranja 290/200; Melón 445/250) and net weight per vegetable (most 150 g; Cebolla, Zanahoria, Calabaza 100 g; Maíz en conserva 70 g); Tabla 3: nutritional value of the exchange per fruit/vegetable |
| Group results | Vegetables 139.44 g (SD ±21.98); fruits 137.68 g (±49.61); dried fruit 28.00 g (±7.53) |
| Population | Spanish general population (guideline use) |
| Licence | **B, CC BY-SA 4.0**. Attribution, and **ShareAlike**: the table we derive from it (the seeded CSV) has to be released under CC BY-SA too. That's acceptable for a data file; it does not affect the application code |
| Role | **The only openly licensed Spanish per-food portion and unit-weight table found.** First seed for fruit and vegetable unit weights and gross→net |
| Official source | https://www.renhyd.org/renhyd/article/view/628 |

#### R6. BEDCA — Base de Datos Española de Composición de Alimentos (historical: removed in FD-033 phase E)

| Field | Value |
| --- | --- |
| Institution | AESAN / red BEDCA, EuroFIR specifications (June 2009) |
| Version | v1.0 (2010), with later "BEDCA2" rows visible in `f_origen` of our CSV |
| Content | 957 foods in our copy (other retrievals report 969 public foods and 56 components); per 100 g **edible portion**; `edible_portion` factor (e.g. `Patata, cruda` 0.81, `Huevo de gallina fresco` 0.88); **separate raw and cooked foods** (`Arroz integral, crudo` / `hervido`, `Lenteja, hervida`, `Patata, cruda/hervida/asada`); LanguaL codes per food (`langual` column, not stored today) |
| Servings / household measures | **None** |
| Licence | **Non-commercial with attribution; no modification.** Reuse in electronic format beyond personal/educational/non-commercial requires AESAN/BEDCA's express authorisation. In our scheme that's **B within a non-commercial app, C otherwise** |
| Role | Stays the composition half. Can be combined with independent ration tables because a ration row only points at a BEDCA id and never rewrites a BEDCA value |
| New use this plan makes of it | Its raw/cooked split, its `edible_portion`, and its LanguaL facets (§6.4) |

#### R7. TABULA (CEU San Pablo, Instituto CEU Alimentación y Sociedad)

| Field | Value |
| --- | --- |
| Lead | Gregorio Varela Moreiras |
| Content | Composition **as declared on the label** of processed products marketed in Spain: brand, legal and commercial name, ingredients, allergens, nutrition declaration; EuroFIR categorisation; WHO Europe nutrient profile. >6,500 products at launch (Dec 2023); **v3.0 (Sept 2025), >10,000 products** |
| Serving / portion info | Not found; the web search tool is the access path |
| Download | No bulk download found; the site returned 403 to the fetch tool |
| Licence | **E → C**. No terms found; ask CEU |
| Role | Complements `food_items` (branded), **not** the ration layer. Low priority for this plan |
| Source | https://ias.ceu.es/tabula-bbdd/presentacion/ |

#### R8. DIAL (UCM Dpto. Nutrición + Alce Ingeniería)

| Field | Value |
| --- | --- |
| What it has | Composition table of >1,000 foods and ~140 components (UCM tables: Ortega, López-Sobaler et al.); **household-measure table of ~1,860 entries**; recommended intakes; current Spanish dietary recommendations; diet planning and evaluation |
| Equivalence / exchange tables | Not advertised |
| Age-specific | Recommended intakes by age/sex (the product's purpose) |
| Access | Commercial Windows software; a licence comes with the webinar registration (Dec 2026 session announced) |
| Downloadable / reusable data | **No.** No statement that the tables can be exported or reused |
| Licence | **D** (C if UCM/Alce would license the household-measure table, which is the one thing here nothing open replaces) |
| Role | Reference only. **Ask UCM/Alce about licensing the medidas caseras table**; it's the best-fitting Spanish household-measure dataset found |
| Source | https://www.ucm.es/idinutricion/programa-dial , https://www.alceingenieria.net/infodial.htm |

#### R9. Moreiras O., Carbajal Á., Cabrera L., Cuadrado C. — *Tablas de composición de alimentos. Guía de prácticas*

- Ediciones Pirámide, **20ª ed. revisada y ampliada, Nov 2022**, ISBN 978-84-368-4657-7, UCM authors.
- >900 foods per 100 g **edible portion**, with edible-portion factors. Household measures and
  rations inside the book: *(verify, not confirmed from the publisher page)*.
- Licence **D** (commercial book). Role: cross-check of edible-portion factors and of Spanish
  unit weights.

#### R10. FINUT / SEÑ — *Guía fotográfica de porciones de alimentos consumidos en España*

- Ruiz-López MD, Martínez de Victoria E, Gil Hernández Á. FINUT, **2019**, ISBN 978-84-09-08860-7.
- **944 photographs, 204 foods, 12 groups**, with portion weight tables; methodology
  standardised per EFSA; multiple sizes, usable across ages; used in the EsNuPI study (children
  1–9 y).
- Printed book (€65). Licence **D**; photos certainly not reusable without permission (**C**).
- Role: the Spanish **visual portion** reference. The UI can *cite* it ("ver guía FINUT, p. X")
  but can't show its photographs.

#### R11. ENALIA / ENALIA 2 (AESAN national consumption surveys)

- ENALIA: children and adolescents **6 months–17 years**, 1,862 participants (2012–2014); 2-day
  diaries (6 m–10 y) and 24-h recalls (11–17 y); an **online photographic atlas of 57 products and
  recipes** in several portion sizes plus household-measure weights. ENALIA 2: adults, elderly and
  pregnant women.
- Values are **observations** (typical consumed portions), not recommendations.
- Microdata reach EFSA's Comprehensive European Food Consumption Database.
- Licence: report **B** *(verify, AESAN notice)*; atlas images **C**.
- Role: evidence for "typical portion" by age, and for resolving conflicts (§4). Not a
  prescription source.
- Source: https://www.aesan.gob.es/AECOSAN/web/seguridad_alimentaria/subdetalle/enalia.htm

#### R12. Spanish exchange systems

**R12a. Russolillo G., Marques-Lopes I. — *Sistema de intercambios para la confección de dietas y planificación de menús*** (2nd ed. 2011, ISBN 978-84-936109-5-1; lists also in *Rev Esp Nutr Hum Diet* 2009;13(3); companion *Álbum fotográfico de porciones de alimentos*, 2008).
- 1 exchange = **10 g** of the characteristic macronutrient: carbohydrate, protein or fat.
- Groups: lácteos; verduras-hortalizas-frutas (fresh, dried, juices); azúcares-repostería;
  cereales-tubérculos-legumbres; alimentos proteicos (carnes, pescados, huevos); grasas (aceites,
  grasas, frutos secos), with **subgroups by energy** (e.g. whole dairy ≈10 g CHO, 7 g protein, 7 g
  fat, 130 kcal: 2 natural yogurts (250 g), 1 cuajada (135 g), 1 glass of milk (200 ml)).
- Allocation order: CHO exchanges → protein → fat; groups: dairy, vegetables, fruit, sugars,
  cereals/tubers/legumes, protein foods, fats.
- Examples (from UCM teaching slides, A. Carbajal 2018): 1 CHO exchange = 50 g potato = 200 ml milk
  = 100 g mandarin = 300 g tomato; 1 protein exchange = 50 g chicken; 1 fat exchange = 10 ml oil.
- General diet planning **and** clinical use. Book: licence **D/C**. The *definition* (10 g per
  unit) is a method, not a table, and can be implemented from BEDCA; the food lists can't be copied.

**R12b. Carbohydrate rations for diabetes, "1 ración de HC = 10 g de hidratos de carbono".**
- *Tabla de raciones de hidratos de carbono* (S. Murillo, Fundación para la Diabetes Novo
  Nordisk): food, grams per ración, usual household measure, glycaemic index. PDFs are image-only.
- Hospital Sant Joan de Déu Barcelona guide for type 1 diabetes: same 10 g definition (site
  certificate was broken at research time).
- **Clinical** (diabetes). Licence **C**. Role: a separate, clinical equivalence system (§6.3),
  **computed from BEDCA carbohydrate**, never copied.

**R12c. Energy-equivalence lists (Salas-Salvadó J. et al., *Nutrición y dietética clínica*, Elsevier, 2014, as cited in UCM teaching material).**
- 7 groups with fixed kcal per unit: lácteos 75 kcal (200 ml skimmed milk), cárnicos 150 kcal
  (100 g chicken, 120 g hake, 75 g canned tuna, 2 eggs), farináceos 100 kcal (40 g bread, 100 g
  potato, 40 g lentils **raw**, 30 g pasta **raw**, 25 g María biscuits), frutas 50 kcal (200 g
  melon, 120 g apple, 60 g banana), verduras 50 kcal (300 g lettuce, 200 g green beans, 150 g
  artichoke), grasas 90 kcal (10 g olive oil, 12 g butter, 50 g pitted olives, 15 g almonds),
  miscelánea 40 kcal (10 g sugar, 15 g honey/jam).
- Hypocaloric diets (clinical). Book **D**. Role: shows the energy-equivalence *method*, which
  fdiet already applies in `alternative/` (`grams` equivalent by energy).

#### R13. Paediatric Spain

- **AEP — *Recomendaciones de la AEP sobre alimentación complementaria*** (Comité de Lactancia
  Materna + Comité de Nutrición, Nov 2018). Healthy infants; introduction ages and textures;
  quantities are mostly qualitative ("la cantidad… puede ser diferente en función de la densidad
  energética"), dairy ~500 ml/day from about 12 months *(verify)*. Licence **D/E**. Role: rules
  and cautions for 6–24 months, not gram rations.
- **ASPCAT — *L'alimentació saludable en la primera infància (0-3 anys)*** (Agència de Salut
  Pública de Catalunya, 2022 edition; Catalan and Spanish, plus other languages); sample menus
  0–3, vegetarian/vegan 0–3 menus. Portion quantities by age *(verify)*. Licence **E** (check
  Scientia Salut record). Role: best candidate for **1–3 years** quantities; Tier 1 regional.
- **SENC 2018 (R1)** and **AESAN 2010 school consensus (R3)** cover 3–18.
- **FINUT healthy lifestyle pyramid** (Gil Á. et al.; the children and adolescents version was
  published in *Nutr Hosp* 2008, the general FINUT guide in 2015). Frequencies and meal structure
  (5 meals/day) more than grams. **D**.

#### R14. Older adults Spain

- SENC 2018 Anexo 1.4 **Adultos-adultos mayores** column, with the note "ajustar la frecuencia
  y el tamaño de las raciones a la situación funcional y de salud"; e.g. rice/pasta 50–70 g,
  eggs 50–70 g, legumes 50–70 g, cheese 30–40 g cured, water 6–8 rations/day for "ancianos".
- FEN — *Guía de alimentación saludable para personas mayores* (PDF on fen.org.es) *(verify
  content and year)*. **D**.
- Clinical (frailty, sarcopenia, dysphagia): ESPEN guideline on clinical nutrition and hydration
  in geriatrics (2019, updated 2022). **Clinical only**, not general rations. **D**.

### 2.3 Tier 2 — Europe

| # | Resource | Institution / year | What it gives | Licence | Role |
| --- | --- | --- | --- | --- | --- |
| E1 | Dietary Reference Values + **DRV Finder** | EFSA; summary v4 2017, finder updated since (sugars, Se, B6, E, Mn, D, A, folate, Fe added) | Energy and nutrient reference values by age/sex/pregnancy/lactation | **B**: EFSA web content reuse authorised with acknowledgement (per-document conditions may apply) | Energy/nutrient targets per patient profile (the "Objetivo" line) |
| E2 | Comprehensive European Food Consumption Database; **FoodEx2** classification | EFSA | Observed consumption incl. ENALIA/ENALIA 2; standard food classification | **B** | Typical-portion evidence; a neutral group vocabulary to map each source's groups onto |
| E3 | EuroFIR recipe calculation guidelines; **Bognár A. 2002**, *Tables on weight yield of food and retention factors…* (BFE Karlsruhe) | EuroFIR; BFE | **Weight yield factors** raw→cooked, retention factors | EuroFIR docs **E**; Bognár report **E/C** | Raw↔cooked conversion when BEDCA lacks the cooked food |
| E4 | Carruba M.O. et al., *Role of Portion Size in the Context of a Healthy, Balanced Diet: A Case Study of European Countries*, IJERPH 2023;20(6):5230 | Peer-reviewed | Cross-country comparison of standard portions (bread/cereals 100 g EE/HU/NO vs 250 g BE/DE; vegetables 80 g MT/PL/SI/UK vs 300 g AM/HU) | **B, CC BY 4.0** (MDPI) | Evidence that portions are national; documents why we never merge countries |
| E5 | Vilela S. et al., *Validation of a picture book to be used in a pan-European dietary survey*, Public Health Nutr 2018 | EFSA EU Menu | Validated picture-book methodology | **D** | Methodology reference for photo portions |
| E6 | JRC Health Promotion Knowledge Gateway, FBDG table | European Commission | Food-based dietary guidelines per EU country, incl. Spain | **B** *(verify)* | Context only |
| E7 | Italy: SINU LARN "porzioni standard" / CREA *Linee guida* 2018 | SINU / CREA | Standard portion concept, per food | **D** *(verify)* | Example of a national standard-portion table; not for Spanish values |

### 2.4 Tier 3 — UK, US, international

| # | Resource | Institution / year | What it gives | Licence | Role |
| --- | --- | --- | --- | --- | --- |
| I1 | **FoodData Central**: SR Legacy / FNDDS / Foundation **food portion** files | USDA ARS | Household measures with gram weights per food (`amount`, `measure_unit`, `modifier`, `portion_description`, `gram_weight`) | **A, CC0 1.0** (source attribution requested) | **Openly licensed fallback for household-measure → gram weights** (US measures: cup, tbsp, "1 medium"). Always labelled US |
| I2 | **USDA Table of Cooking Yields for Meat and Poultry**, Release 2 (2014) | USDA NDL | Cooking yield %, moisture and fat change per cut and method; CSV on Ag Data Commons | **A** (US federal work) | Raw↔cooked for meat/poultry |
| I3 | USDA Nutrient Retention Factors (Release 6, 2007) | USDA NDL | Retention factors | **A** | Secondary to BEDCA cooked rows |
| I4 | **FAO/INFOODS Density Database v2.0** (2012) | FAO/INFOODS | g/ml per food (oils, milk, flours, …) | **C**: rights queries to copyright@fao.org | ml → g for foods measured by volume (oil ≈0.91 g/ml, not 1) |
| I5 | FAO/INFOODS Guidelines for Food Matching v1.2; Guidelines for converting units | FAO | Methods | **B/D** | Method reference |
| I6 | *Choose Your Foods: Food Lists for Diabetes*, 5th ed. (2019), and *…for Weight Management* | American Diabetes Association + Academy of Nutrition and Dietetics | Exchange/"choices": starch 15 g CHO, 3 g protein, ≤1 g fat, 80 kcal; fruit 15 g CHO, 60 kcal; fat-free milk 12 g CHO, 8 g protein, 100 kcal; non-starchy vegetables 5 g CHO, 2 g protein, 25 kcal; lean protein 7 g protein, 2 g fat, 45 kcal; fat 5 g, 45 kcal *(verify against the 2019 booklet)*. Spanish-language edition exists | **D** (©ADA/AND 2019, sold) | Reference for the US exchange definition only |
| I7 | Dietary Guidelines for Americans 2025–2030 (released 7 Jan 2026) + *Scientific Foundation*; 2025 DGAC Part D ch. 7 "Portion Size" | USDA/HHS | US patterns; protein 1.2–1.6 g/kg/day stated | **A** | International context only; never replaces Spanish values |
| I8 | UK *Food Portion Sizes*, 3rd ed. (Crawley, Mills, Patel; FSA, TSO 2002); FSA-commissioned photographic atlases for children 1.5–16 y (2007, NDNS) | FSA | Average portion weights by food; child atlases | **D** (Crown copyright, out of print) | UK reference; age-banded atlas methodology |
| I9 | McCance & Widdowson's CoFID | UK OHID/PHE | Composition | **B, OGL** | Only if BEDCA lacks a food; not needed for this plan |
| I10 | **Sistema Mexicano de Alimentos Equivalentes** (SMAE), Pérez-Lizaur et al., Fomento de Nutrición y Salud | Mexico | Full exchange system in Spanish, e.g. cereals 15 g CHO, 2 g protein, 70 kcal per equivalent | **D** (commercial book) | Spanish-language exchange reference; Mexican foods and measures, so **never** a Spanish value |
| I11 | Carbohydrate units elsewhere: UK DAFNE 10 g CP; US "carb choice" 15 g; German BE 12 g / KE 10 g *(verify)* | Various | Definition only | — | Shows why the unit size has to be a column, not a constant |
| I12 | WHO *Healthy diet* fact sheet (≥400 g fruit and vegetables/day); WHO 2023 *Guideline for complementary feeding of infants and young children 6–23 months* | WHO | Population targets; infant feeding | **B** (CC BY-NC-SA 3.0 IGO) *(verify per document)* | International floor for fruit/veg; infant rules |
| I13 | ESPGHAN position paper on complementary feeding (Fewtrell et al., JPGN 2017) | ESPGHAN | Infant feeding | **D** | Paediatric reference |

### 2.5 Tier 4 — secondary (not seeded)

UCM **INNOVADIETA** (Carbajal Á. et al.): directories of ration tables, photo guides and
exchange lists, plus teaching slides. It's the best map of the Spanish literature, but it's
secondary. Use it to find originals, never as the cited source. Also here: Fisterra *Dieta por
intercambios* (Elsevier), Quiles 2016 blog, commercial apps. Older Spanish standard-ration
papers worth locating if a gap stays open: Alcoriza et al. 1990 (*Nutrición Clínica* 10/2), De Cos
et al. 1991 (*Nutrición Clínica* 11/3), Vázquez, de Cos, Hortelano 1998 (*Tablas de raciones
estándar de alimentos*, Díaz de Santos).

### 2.6 Category key and matrix

Categories: 1 composition DB · 2 ration table · 3 portion-size DB · 4 household-measure DB ·
5 equivalence table · 6 exchange system · 7 paediatric · 8 adult · 9 elderly · 10 daily/weekly
recommendation · 11 meal distribution · 12 clinical exchange · 13 photographic guide ·
14 international reference · 15 scientific guideline.

| Resource | Rations | Portions / unit weights | Household measures | Equiv. | Exchanges | Paed. | Adult | Older | Raw/cooked | Edible | Download | Licence |
| --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | --- | :-: |
| R1 SENC 2018 | ✔ | – | ✔ | – | – | ✔ 3-6/7-12/juvenil | ✔ | ✔ (note) | ✔ | partial | PDF | D |
| R2 AESAN 2022 | ✔ | – | ✔ | – | – | – | ✔ | – | partial ("en seco") | – | PDF | B* |
| R3 AESAN 2010 school | ✔ | – | ✔ | – | – | ✔ 3-6…16-18 | – | – | ✔ per row | ✔ neto/bruto | PDF | B* |
| R4 RD 315/2025 | freq. | – | – | – | – | ✔ school | – | – | – | – | BOE | A |
| R5 5 al día 2019 | ✔ | ✔ | ✔ | exch. values | – | – | general | – | partial | ✔ gross/net | PDF | B (CC BY-SA) |
| R6 BEDCA | – | – | – | – | – | – | – | – | ✔ separate foods | ✔ factor | CSV (ours) | NC+attr |
| R8 DIAL | – | ✔ | ✔ ~1,860 | ? | ? | ✔ intakes | ✔ | ✔ | ? | ? | software | D |
| R10 FINUT photo guide | – | ✔ | – | – | – | ✔ | ✔ | – | ? | ? | book | D |
| R12a Russolillo | ✔ | ✔ | ✔ | ✔ | ✔ 10 g | – | ✔ | – | ✔ | ✔ | book | D/C |
| R12b HC rations | – | – | ✔ | ✔ | ✔ 10 g CHO | ✔ | ✔ | – | ✔ | ? | image PDF | C |
| I1 FoodData Central | – | ✔ | ✔ (US) | – | – | – | – | – | ✔ separate foods | ✔ refuse % | CSV/API | A |
| I2 USDA yields | – | – | – | – | – | – | – | – | ✔ yields | – | CSV | A |
| I4 FAO density | – | – | ml→g | – | – | – | – | – | – | – | XLSX | C |
| I6 ADA/AND lists | – | ✔ | ✔ (US) | ✔ | ✔ 15 g | – | ✔ | – | ✔ | – | booklet | D |

`?` = not established. `B*` = AESAN legal notice to be re-verified.

---

## 3. Values extracted, with provenance

These are the candidate seed rows. Units, state and population are recorded exactly as the
source gives them. Ranges stay ranges.

### 3.1 Adult rations — AESAN 2022 (R2, pp. 52–53)

| Group | Ration | State / basis | Household measure (verbatim) |
| --- | --- | --- | --- |
| Hortalizas | 150–200 g | – | 1 plato de ensalada variada; 1 plato de hortaliza cocida; 1 crema de hortalizas |
| Frutas | 120–200 g | fruta fresca | 1 pieza mediana; 1 tazón mediano de cerezas o fresas; 2 rodajas medianas de melón o sandía |
| Patatas y tubérculos | 150–200 g | – | 1 patata grande o 2 pequeñas |
| Cereales: pan | 40–60 g | – | 3-4 rebanadas o un panecillo |
| Cereales: pasta/arroz | 60–80 g | **en seco** | 1 plato normal |
| Legumbres | 50–60 g | **en seco** | 1 plato normal individual |
| Frutos secos | 20–30 g | – | 1 puñado |
| Pescado | 125–150 g | – | 1 filete individual o varias porciones de marisco |
| Huevos | 1 huevo mediano (53–63 g) | – | – |
| Leche | 200–250 ml | – | 1 vaso/taza de leche |
| Queso fresco | 85–125 g | – | – |
| Queso curado | 40–60 g | – | 2-3 lonchas de queso |
| Yogur y leches fermentadas | 125 g | – | 1 unidad de yogur |
| Carne | 100–125 g | – | 1 filete mediano; 1 cuarto de pollo; 1 cuarto de conejo |
| Aceite de oliva | **10 ml** | – | **1 cucharada sopera** |

Frequencies (R2, pp. 50–51), Spanish population: vegetables+fruit ≥5/day (≥3 vegetables, 2–3
fruit); cereals 3–6/day (≤4 if calories are restricted); legumes ≥4/week up to daily; nuts ≥3/week
up to 1/day; fish ≥3/week; eggs up to 4/week; dairy **max 3/day**; meat **max 3/week**; olive oil
in every main meal (amount to the energy target); one protein ration of lunch and dinner from
plant sources, preferably legumes.

### 3.2 Rations by age — SENC 2018 (R1)

Anexo 1.3 **Adultos**, "Peso de la ración (crudo y neto)":

| Group | Weight | Household measure |
| --- | --- | --- |
| Agua | 200 ml | 1 vaso |
| Pan | 30–60 g | 1-2 trozos de 4 dedos de grosor o un panecillo |
| Arroz o pasta | 50–80 g | 1 plato hondo normal |
| Cereales de desayuno | 20–40 g | 2-4 cucharadas soperas |
| Patatas | 150–200 g | 1 patata mediana o 2 pequeñas |
| Frutas | 150–200 g | 1 pieza mediana, 2 mandarinas pequeñas, 3 ciruelas, 1 rodaja de melón, 1 rodaja de sandía, 1 taza de cerezas, fresas u otros frutos del bosque |
| Verduras y hortalizas | 150–250 g | 1 plato hondo de ensalada variada / de verdura cocida / de puré o crema; 1 tomate grande |
| Aceite de oliva virgen extra | **15 ml** | **1 cucharada sopera** |
| Leche | 200–250 ml | 1 vaso de leche |
| Yogur | 125 g | 1 yogur |
| Queso curado/semicurado | 40–60 g | 2-3 cuñas de queso |
| Queso fresco | 80–125 g | 1 porción individual |
| Pescados y mariscos | 100–150 g | 1 filete mediano, 1 pieza de ración mediana, 1 rodaja mediana, 3-4 unidades |
| Carnes | 100–150 g | 1 filete mediano, 1 muslo de pollo mediano, 1 pechuga |
| Huevos | 65–100 g | 1 huevo grande (L) / 2 huevos pequeños (S) o medianos (M) |
| Legumbres | 60–80 g | 1 plato hondo normal, 2-3 cazos pequeños (con caldo) |
| Frutos secos | 20–30 g | 1 puñado (sin cáscara) |

Anexo 1.2, children and young people, "Peso ración crudo" *(verify column alignment; the
extraction lost the group column on p. 27)*:

| Group | 3-6 años | 7-12 años | Etapa juvenil |
| --- | --- | --- | --- |
| Pan | 30 g · rebanada 2 dedos | 40 g · rebanada 2 dedos | 60 g · rebanada 4 dedos / bollito mediano |
| Arroz | 50 g · 2,5 cdas soperas crudo | 60 g · 3 cdas soperas | 80 g · 4 cdas soperas |
| Pasta | 40 g · 4 cdas soperas crudo | 60 g · 6 cdas soperas | 80 g · 8 cdas soperas |
| Patatas | 100–150 g · 1 ud mediana | 100–150 g · 1 ud mediana | 150–200 g · 1 patata grande |
| Legumbres | 30 g · 2 cdas soperas crudo (escurrido) / 1 cazo pequeño | 45–60 g · 3-4 cdas / 2 cazos | 80 g · 6 cdas / 3 cazos |
| Verduras y hortalizas | 120–150 g · 1 plato llano mediano | 120–150 g | 150–250 g · 1 plato llano grande |
| Frutas | 80–100 g · 1 pieza mediana | 150–200 g | 150–200 g |
| Aceite de oliva | **10 ml · 1 cucharada mediana** | 10 ml | 10 ml |
| Leche | 250 ml · 1 tazón / 100 ml postre | 250 ml / 100 ml | 250 ml / 100 ml |
| Yogur | 125 g · 1 ud | 125 g | 125 g |
| Queso | 25–30 g curado / 40–75 g fresco | 50–60 g curado / 80–125 g fresco | same as 7-12 |
| Pescado | 70–80 g porción comestible · 1 filete pequeño; 125–150 g pieza · media pieza; 40–50 g azul pequeño | 100–120 g filete; 250–300 g pieza; 40–50 g azul | 125–150 g filete; 250–300 g pieza; 50–80 g azul |
| Carnes | 30–60 g filete/picada/guiso; 80–90 g con hueso | 80–85 g; 100–140 g con hueso | 100–120 g; 150–180 g con hueso |
| Huevos | 1 ud | 1-2 uds | 1-2 uds |
| Embutidos | 25–30 g · 1 loncha fina / 6 rodajas finas | 25–30 g | 25–30 g |

Anexo 1.4 frequencies and weights *(verify)*: **Niños/as**: fruit 3-4/day 80–150 g; vegetables
2-3/day 120–150 g; oil 3-4/day 10 ml; dairy 3/day (150–220 ml milk; 125 g yogurt; 20–40 g cured
cheese; 60–80 g fresh); fish 3-4/week 50–100 g; white meat 3/week 50–100 g; eggs 3/week 50–65 g;
legumes ≥2-4/week 30–60 g; nuts 3-7/week 15–20 g. **Adolescentes**: dairy 4/day; eggs 65–100 g;
legumes 60–80 g; oil 15 ml. **Adultos-adultos mayores**: rice/pasta 50–70 g; potatoes 100–150 g;
dairy 2-3/day (30–40 g cured cheese, 60–80 g fresh); meat 100–125 g; eggs 50–70 g; legumes
50–70 g; water 4-6/day adults, 6-8/day "ancianos".

### 3.3 School lunch rations — AESAN 2010 consensus (R3, Anexo II) *(verify column alignment)*

| Item (role) | 3-6 años | 7-12 años | 13-15 años | 16-18 años | Basis |
| --- | --- | --- | --- | --- | --- |
| Legumbres (plato principal) | 30 g · 2 cdas soperas | 60 g · 4 cdas | 60 g · 4 cdas | 90 g · 6 cdas | (1) crudo y neto |
| Legumbres (guarnición) | 15 g | 30 g | 30 g | 30 g | (1) |
| Patatas (plato principal) | 150–200 g | 200–250 g | 200–250 g | 200–250 g | (1) |
| Arroz, pasta (plato principal) | 50–60 g · 1 plato pequeño hondo | 60–80 g | 80–90 g | 80–90 g | (2) crudo, medida en cocido |
| Arroz, pasta (sopa) | 20–25 g | 20–25 g | 20–25 g | 20–25 g | (2) |
| Pan (barra) | 30 g · 3 dedos | 30 g | 60 g · 6 dedos | 60 g | – |
| Verduras (plato principal) | 120–150 g | 120–150 g | 200–250 g | 200–250 g | – |
| Filete de carne | 50–60 g | 80–90 g | 110–120 g | 110–120 g | (1) |
| Pollo guisado/asado | 80–90 g | 150–160 g | 230–250 g | 300–320 g | (5) crudo y **bruto** |
| Pescado en filetes | 70–80 g | 100–120 g | 150–160 g | 150–160 g | (1) |
| Huevos | 1 ud | 1-2 uds | 2 uds | 2 uds | – |
| Fruta fresca | 80–100 g | 150–200 g | 150–200 g | 150–200 g | (5) bruto |
| Queso (ración) | 25–30 g · 1 loncha fina | 50–60 g | 50–60 g | 50–60 g | – |
| Leche (postre) | 100 ml | 200 ml | 200 ml | 200 ml | – |

### 3.4 Unit weights — 5 al día 2019 (R5, Tabla 5, CC BY-SA 4.0)

Fresh fruit, mean **gross → net** g: aguacate 100→60 · albaricoque 195→180 · cerezas 145→120 ·
ciruelas 120→100 · granada 215→120 · higos 160→120 · kiwi 100→80 · mandarina 170→120 ·
mango 190→120 · manzana 200→160 · melocotón 165→100 · melón 445→250 · naranja 290→200 ·
nectarina 165→100 · nísperos 322→200 · pera con piel 174→150 · plátano 120→80 · sandía 395→250 ·
uvas 125→120; net only: arándanos/frambuesa/moras 125, fresa 250, piña sin piel 120.
Dried fruit net: albaricoque seco 30, ciruela seca 40, dátil 20, higo seco 30, pasas 20.
Vegetables net: 150 g for most; calabaza, cebolla, chirivía, nabos, rábano, zanahoria 100 g;
maíz en conserva 70 g.

### 3.5 Meal energy distribution

| Source | Population | Desayuno | Media mañana | Comida | Merienda | Cena |
| --- | --- | --- | --- | --- | --- | --- |
| AESAN/MEC 2010 (R3) | School-age | 25% (or 15%) | – (or 10%) | 35% | 10% | 30% |

UCM teaching slides show 20/15/30/15/20 for a 2,500 kcal diet. That's a **worked example, not a
recommendation**, so it isn't seeded. No authoritative Spanish adult distribution was found in a
Tier 1 document during this research. Leave adults without a default and let the nutritionist set
one (open question Q4).

---

## 4. Conflicts, and what causes them

Nothing here is resolved by choosing one number. Each row says why the sources differ, and §7
shows how the UI keeps the choice visible.

| Item | AESAN 2022 | SENC 2018 | Other | Likely cause |
| --- | --- | --- | --- | --- |
| **1 cucharada sopera of oil** | 10 ml | 15 ml (adults); 10 ml "cucharada mediana" (children) | UCM slides: "una cucharada sopera, 10 g"; "1 cucharada sopera rasa de aceite (11 g)" | Different spoon definitions (15 ml is the metric tablespoon; Spanish kitchen spoons are smaller); rasa vs colmada; ml vs g (oil ≈0.91 g/ml) |
| **1 cucharadita (`cdta`)** | – | – | **No Tier 1 value found** | Gap. The most frequent unweighed ingredient in our own workbook has no authoritative weight (§8) |
| Egg | 1 medium, 53–63 g | 65–100 g (1 L or 2 S/M); older adults 50–70 g | EU size classes S<53, M 53–63, L 63–73, XL ≥73 g (Reg. (EC) 589/2008) *(verify)* | AESAN = one medium egg; SENC = a ration that may be two eggs; gross (shell) vs net is not stated by either |
| Legumes | 50–60 g dry | 60–80 g raw (annex) and 150–200 g cooked (text); older adults 50–70 g | School: 30–90 g by age | Population (sustainability-driven AESAN vs SENC), and **state** (dry vs cooked) |
| Rice/pasta | 60–80 g dry | 50–80 g raw; older adults 50–70 g | School: 50–90 g by age and role | Role (plato principal vs guarnición vs sopa), age |
| Fruit | 120–200 g fresh | 150–200 g | 5 al día mean 137.68 g net, per fruit 80–250 g net; school 80–100 g (3-6) | Group range vs per-food calibre; gross vs net |
| Vegetables | 150–200 g | 150–250 g | 5 al día mean 139.44 g net; school 120–150 g (3-6) | Per food vs group; age |
| Fish | 125–150 g | 100–150 g | School fillet 70–160 g by age | Population; edible portion vs piece |
| Meat | 100–125 g | 100–150 g adults; 100–125 g older | School fillet 50–120 g, chicken with bone 80–320 g | Bone-in (gross) vs fillet (net) |
| Nuts | 20–30 g | 25 g (text) vs 20–30 g (annex) | 5 al día **dried fruit** 28 g | Internal inconsistency in SENC; "frutos secos" (nuts) vs "frutas desecadas" (dried fruit) are different foods sharing a word |
| Dairy per day | max 3 | 2-3 adults; 3 children; **4 adolescents** | – | Age; AESAN caps for sustainability |
| Eggs per week | up to 4 | 3–5 units/week (1.3); 3 rations/week (1.4) | RD 315: 1–2 per 5 school lunches | Unit (egg vs ration) and meal context |
| Fruit per day | 2–3 | 3–4 | – | AESAN splits 5-a-day as ≥3 veg + 2-3 fruit; SENC the other way |
| Carbohydrate unit | – | – | Spain 10 g; US 15 g; UK DAFNE 10 g; DE BE 12 g | National clinical convention |

**Rule for the product:** a diet uses **one reference profile** (source + population band) at a
time, chosen by the nutritionist, and every derived gram and ration count shows that profile's
name. Switching profile recomputes the counts. Nothing is averaged across sources.

---

## 5. What we can load, what we can only cite

| Use | Sources that allow it now | Needs permission first |
| --- | --- | --- |
| Ration definitions (group-level weight ranges + measure text) | AESAN 2022 (B*), AESAN 2010 school (B*), RD 315/2025 frequencies (A) | SENC 2018 (whole annexes), Russolillo lists |
| Per-food unit weights, gross→net | 5 al día 2019 (CC BY-SA), USDA FDC (CC0, US measures), BEDCA `edible_portion` (NC) | DIAL household-measure table, Moreiras, FINUT photo guide |
| Density ml→g | none open | FAO/INFOODS Density DB |
| Raw↔cooked yields | USDA cooking yields (A), BEDCA cooked foods (NC) | Bognár 2002 / EuroFIR |
| Exchange **definitions** (10 g CHO/protein/fat; 15 g US) | Definitions are methods; implement against BEDCA figures, cite the originating system | Copying any published exchange **food list** |
| Photos | none | FINUT, Russolillo album, ENALIA atlas |

Consequences for the design:

1. **Exchanges are computed, not imported.** "How many 10 g-carbohydrate exchanges is 70 g of raw
   rice" is BEDCA carbohydrate × grams ÷ 10, derived on read like kcal. The exchange *system* row
   stores only the definition and its citation. That avoids copying anyone's list and keeps
   BEDCA values unmodified.
2. **Every seeded row carries `source_id`, page/table reference, and retrieval date.** Seeded CSVs
   under a CC BY-SA source are marked as such, in the file header and in the source row.
3. **Attribution is shown wherever the figure is shown**, the same way BEDCA's is required to be.
   One footer component lists every source the current screen used.

---

## 6. Backend changes

### 6.1 A new bounded context: `reference`

`src/main/java/com/fdiet/reference/`, the nutrition reference layer. It owns its tables, adds
**no** job to `food`, `diet` or `alternative`, and follows the house rules (interface injection,
one owning service per table, DTOs across boundaries, derived figures never stored).

Tables (`V8__create_reference_schema.sql`):

```text
ref_sources
  id, code (e.g. AESAN-2022-007), name, institution, country (ES|EU|UK|US|INT),
  tier (1-4), year, version, published_on, retrieved_on, url, page_note,
  licence_class (A-E), licence_text, attribution, scope, clinical (bool), notes

ref_populations                      -- the source's own band, kept verbatim (Age Rule)
  id, source_id, label (verbatim: "7-12 años", "Etapa juvenil", "Adultos-adultos mayores"),
  age_min_months NULL, age_max_months NULL,   -- only when the source states them
  sex (ANY|MALE|FEMALE), physiological_state (NONE|PREGNANCY|LACTATION),
  health_status (GENERAL|CLINICAL), clinical_condition NULL, context NULL ("comedor escolar")

ref_rations                          -- a defined standard serving
  id, source_id, population_id, group_label (verbatim), food_label (verbatim) NULL,
  bedca_food_id NULL,                 -- only when the row names one food
  role NULL (PLATO_PRINCIPAL|GUARNICION|SOPA|POSTRE),
  grams_min NULL, grams_max NULL, ml_min NULL, ml_max NULL, units_min NULL, units_max NULL,
  state (RAW|COOKED|DRY|DRAINED|AS_SOLD|UNSPECIFIED),
  weight_basis (NET_EDIBLE|GROSS|UNSPECIFIED),
  household_text (verbatim), page_ref

ref_household_measures               -- the vocabulary: "cucharada sopera", "cdta", "loncha"
  id, code, label_es, aliases (for the parser: cda, cdta, cucharadita, cs, …), kind (VOLUME|PIECE|SLICE|PLATE|HANDFUL|CONTAINER)

ref_food_measures                    -- a measure of a specific food, in grams
  id, source_id, measure_id, bedca_food_id NULL, food_label (verbatim),
  size NULL (SMALL|MEDIUM|LARGE), grams NULL, ml NULL,
  state, weight_basis, page_ref

ref_intake_recommendations           -- how many rations, how often, for whom
  id, source_id, population_id, group_label, rations_min NULL, rations_max NULL,
  period (PER_DAY|PER_WEEK|PER_MEAL|PER_SCHOOL_WEEK), is_maximum (bool), note, page_ref

ref_meal_distributions
  id, source_id, population_id, meal_type (existing MealType), pct_min, pct_max, note

ref_exchange_systems                 -- definitions only (§5.1)
  id, source_id, code, name, country, nutrient (CARBOHYDRATE|PROTEIN|FAT|ENERGY),
  grams_per_unit NULL, kcal_per_unit NULL, clinical (bool), note

ref_group_mappings                   -- a source's group → fdiet's FoodCategory
  id, source_id, group_label, food_category (alternative.domain.FoodCategory), note

ref_yield_factors                    -- raw → cooked weight
  id, source_id, food_label, bedca_raw_id NULL, bedca_cooked_id NULL, method, yield (e.g. 2.6), page_ref
```

Not stored, by design: ration counts, exchange counts, grams resolved from a measure, kcal. All
of it is derived on read.

**Loading.** Curated CSVs under `reference-data/` (one per table, one row per published figure,
each with `source_code` and `page_ref`), imported by `POST /api/reference/sync`, idempotent on a
stable `code`, following `POST /api/bedca/sync`. CSVs are reviewed like code, and a nutritionist
reading the diff can check every number against its page.

Services and endpoints (`ReferenceController` at `/api/reference`):

- `GET /api/reference/sources`: every source with licence class and attribution.
- `GET /api/reference/profiles`: selectable (source × population) pairs that have rations.
- `GET /api/reference/rations?profile=&category=`: ration rows for a profile, grouped by
  `FoodCategory` through `ref_group_mappings`.
- `GET /api/reference/measures?bedcaFoodId=&profile=`: household measures that weigh this food,
  best source first (profile's own source → other Spanish Tier 1 → 5 al día → USDA, each
  labelled).
- `GET /api/reference/recommendations?profile=` and `GET /api/reference/meal-distribution?profile=`.
- `POST /api/reference/sync`.

### 6.2 Patients and diets carry the population

- `V9__patient_profile.sql`: `patients.birth_date DATE NULL`, `patients.sex VARCHAR(8) NULL`.
  Both nullable, since a patient is still a name first. No auth; the principle in CLAUDE.md
  stands.
- `diets.reference_profile_code VARCHAR NULL`: which ration profile the week is written against.
  **A choice, stored**, like a food match. Defaulted in the UI from the patient's age when known
  and never silently changed. Copying a diet copies it; the target patient's age may make the
  UI suggest a different one (warning, not rewrite).
- Pregnancy/lactation/clinical: a diet-level `population_note` for the nutritionist's own text.
  No clinical profile is offered until Phase 5.

### 6.3 Weighing what is written in measures

Current rule (`PortionScaler`): only weights and volumes are weighed. Anything else is
`unmeasured`. The new rule keeps "never guess" and adds one step.

1. **`diet_ingredients.food_measure_id BIGINT NULL`** (FK `ref_food_measures`), plus
   **`diet_ingredients.state VARCHAR(16) NULL`** (RAW/COOKED/DRY/DRAINED/…). Same migration
   `V10__ingredient_measure_state.sql`; same for `extra_foods` so journal and plan keep agreeing
   (the reason `JournalNutritionService` borrows `IPortionScaler`).
2. `PortionScaler.factorOf(quantity, unit)` becomes `factorOf(quantity, unit, measure)`:
   - g/kg/mg/l/ml as today;
   - else, if the ingredient carries a `food_measure_id`: `quantity × measure.grams`, and when the
     measure's `weight_basis` is GROSS and the matched BEDCA food has `edible_portion`,
     × `edible_portion`, because BEDCA figures are per 100 g **edible** portion;
   - else `unmeasured`, as today.
3. **Attaching a measure is a match, and it follows the matching rule.** The resolver attaches a
   `food_measure_id` automatically **only when exactly one** measure row exists for (that BEDCA
   food, that measure alias) in the diet's profile source. Otherwise the fix-up list offers the
   candidates with their sources ("1 cucharada sopera: 10 ml, AESAN 2022 · 15 ml, SENC 2018") and
   the nutritionist picks one with the existing `PATCH /api/diets/{id}/ingredients/{ingredientId}`
   (new optional field `foodMeasureId`).
4. `NutritionSummaryDto` keeps `counted + unmatched + unmeasured == ingredients` and adds
   **`countedByMeasure`**, the subset of `counted` weighed through a household measure, so a total
   says how much of it rests on a conversion. That's the "a total always travels with its counts"
   rule again.
5. **Millilitres.** The 1 g/ml assumption stays for water-like liquids, and is refused for the
   foods where it's wrong by more than a few percent (oils ≈0.91). Those need a food measure row
   in grams or a density; `PortionScaler`'s class note gets the exception.

### 6.4 Raw, cooked, dry, drained

- **Store BEDCA's LanguaL codes.** `bedca_foods.langual VARCHAR NULL` (in V8 or its own migration)
  and read it in `BedcaImportService`. Adding a column doesn't modify any published value. In our
  CSV the raw rows carry `F0003 … G0003` and boiled rows `F0014 … G0014` (fried egg `G0029`,
  roast potato `G0005`, prefried frozen `F0018`). Facet F is heat treatment and facet G is cooking
  method; **confirm the codes against the LanguaL thesaurus before relying on them.** A derived
  `BedcaFoodDto.state` (RAW/COOKED/UNKNOWN), computed on read, never stored.
- **Parser.** `MealTextParser` recognises state words and keeps them on the ingredient: `crudo`,
  `en crudo`, `en seco`, `seco` → DRY/RAW; `cocido/a`, `hervido/a`, `al vapor`, `a la plancha`,
  `asado`, `al horno` → COOKED; `escurrido` → DRAINED; `peso neto`/`bruto` → basis.
  `arroz blanco (70 g crudo)` → `arroz blanco`, 70 g, RAW.
- **Matching.** `NameMatcher` and `FoodResolverService` rank candidates whose derived state agrees
  with the written state first (`lentejas cocidas` → `Lenteja, hervida` above `Lenteja, cruda`).
  Still a ranking: exact-name matching stays the only automatic decision.
- **Mismatch.** Written RAW but matched to a COOKED food (or the reverse) is flagged on the
  ingredient (`stateMismatch: true` in the DTO), shown as a warning chip, and **not converted**.
  A yield factor (USDA for meat; Bognár/EuroFIR once permitted) is offered as a suggestion
  ("70 g crudo ≈ 182 g cocido, factor 2.6, fuente X"), never applied silently.

### 6.5 The parser reads measure-first writing

Today `1 cdta AOVE` becomes an ingredient named `1 cdta AOVE`, 1 `unidad`. Additions:

- A leading count and measure outside brackets: `1 cdta AOVE` → name `AOVE`, 1, unit `cdta`;
  `2 tostadas de pan integral (50 g)` keeps 50 g (the weight still wins, as `readQuantity` already
  decides) and remembers `2 tostadas` as the measure text; `1 kiwi` → name `kiwi`, 1, `unidad`.
- Size words: `pequeño/a`, `mediano/a`, `grande` → `size`, so `1 pera pequeña` can find a SMALL
  row.
- Ranges: `2-3 lonchas de pavo (40-60 gr)` keeps the range text and uses the lower bound only if
  the nutritionist confirms (today it silently takes the last number).
- Aliases come from `ref_household_measures.aliases` (via `IReferenceService`), so the vocabulary
  lives in one table and the parser has no hard-coded list.
- `raw_text` is untouched; the sentence stays the record.

### 6.6 Ration and exchange accounting (derived on read)

A service in `reference`, `IRationAccountingService`, reads a loaded week (no extra queries,
same as `DietNutritionService`) and answers:

- per day and per week, **rations per group** for the diet's profile:
  `grams (in the ration's state and basis) ÷ ration grams`. A range ration gives a range count
  (70 g ÷ 60–80 g = 0.9–1.2) and is shown as a range, never as a hidden midpoint;
- **against the profile's recommendations** (per day, per week, maxima flagged as maxima);
- **exchanges** per system (10 g CHO etc.) from BEDCA figures;
- **energy per meal** as % of the day, against `ref_meal_distributions` when the profile has one;
- **with its counts**: ingredients not assignable to a group (no mapping, uncategorised food, state
  mismatch) are counted and named, never silently left out.

Endpoint: `GET /api/diets/{id}/rations?profile=`. The profile defaults to the diet's own. It lives
in `diet`, not `reference`, because the answer is about a diet and `diet` already reaches foods
through services. `reference` exposes the tables through `IReferenceService`.

### 6.7 Composing from rations: no second parser

The editor must not grow a parser (CLAUDE.md, `POST /api/diets/parse`). So the composer
**writes text the existing parser reads**, and the backend does all resolving:

- `POST /api/reference/compose` takes `{bedcaFoodId, rationId | foodMeasureId, count, grams?, state}`
  and answers the canonical fragment (`arroz blanco (70 g crudo)`,
  `aceite de oliva virgen extra (1 cucharada sopera, 10 ml)`) plus the resolved ingredient. The UI
  appends the fragment to the cell text and calls `parse` as it already does.
- A **range** ration makes the composer ask for a value inside the range (pre-filled with the
  bound the nutritionist last used), so what gets written is always an explicit weight.

### 6.8 Import

`DietImportService` gains nothing new beyond the parser (§6.5) and resolver (§6.3–6.4), which it
already uses. It takes an optional `referenceProfile` parameter so measure attachment has a
profile to look in. Workbooks written in group rations (`1 ración de fruta`) stay unmatched
ingredients labelled as a **ration placeholder** for the nutritionist to fill; nothing picks the
fruit.

### 6.9 Alternatives

`alternative/` keeps category-then-composition. It gains an optional `basis` parameter,
`ENERGY` (today's default) or `CARBOHYDRATE` / `PROTEIN`, and `grams` is solved for that nutrient.
When the diet's profile has a ration for the alternative's group, the answer also says
"≈ N raciones (fuente)". The category rule stays: an equivalence never crosses a category.

### 6.10 Tests

- CSV import: every row has a source, page ref, state and basis; unknown enum → startup failure,
  as with `FoodCategoriser` conflicts.
- `PortionScaler`: gross × `edible_portion`; oil ml refusal; `countedByMeasure` invariant.
- `MealTextParser`: every fragment in §0 (`1 cdta AOVE`, `1 kiwi`, `arroz blanco (70 g crudo)`,
  `2-3 lonchas de pavo (40-60 gr)`, `aguacate (½, 80 g)`), round-tripped against
  `example-ui.xlsx`, with **no change to what is stored for fragments that already parse today**.
- Resolver: one candidate → attached; two sources disagree → suggestions, not attached.
- Ration accounting: ranges divide into ranges; maxima reported as maxima; unmapped foods counted.
- Seed-data test: each value in §3 matches its CSV row (guards transcription).

---

## 7. UI changes (Vue, `UI/src`)

Tone, palette, Spanish copy and breakpoints stay as they are (sober clinical, sage primary, week
grid from 834 px, day view on mobile).

1. **Create and import screens** (missing today). `dietsApi.create` and `dietsApi.import` in
   `api/diets.ts`. A *Nueva dieta* flow on `/dieta`: name, start date, days, slots, **reference
   profile** (defaulted from the patient's age: e.g. "SENC 2018 · 7-12 años", "AESAN 2022 ·
   adultos"), producing an empty week the grid can edit. *Importar Excel* with the same profile
   picker. The empty-state message in `stores/dietDraft.ts` then becomes true.
2. **Profile chip in `EditorHeader.vue`** showing the diet's profile. Changing it recomputes counts
   and never rewrites grams.
3. **`DishEditPanel.vue`: "Añadir por raciones"** below the textarea: search a food (reuse
   `FoodLinkPanel` search) → the profile's ration for its group and the food's household measures
   as chips, each with its source ("1 ración · 60–80 g en seco · AESAN 2022"; "1 cucharada sopera ·
   10 ml · AESAN 2022"; "15 ml · SENC 2018") → count stepper → raw/cooked toggle → *Añadir*
   appends the composed fragment (§6.7). A range asks for a value.
4. **Ingredient chips** gain a measure line ("1 cdta → 5 g · fuente") and a warning chip for
   state mismatch or an unresolved measure, next to the existing "Sin vincular".
5. **`FoodLinkPanel.vue`**: when an ingredient is matched but unmeasured, show measure candidates
   from `GET /api/reference/measures` with their sources; picking one PATCHes `foodMeasureId`.
6. **Rations strip in `DaySummary.vue` / `DayTotalCard.vue`**: per group, rations today against the
   profile's recommendation ("Frutas 2 / 3–4 al día"); a weekly strip for legumes, fish, eggs, meat
   ("Legumbres 3 / ≥4 a la semana"). Maxima styled as maxima. Always labelled *orientativo* with
   the source. Unassigned ingredients counted, as `coverage` already does for kcal.
7. **Meal energy bar** per day: % of kcal per slot, with the profile's distribution as a faint
   target only when the profile has one.
8. **Attribution footer** on every screen that shows reference-derived figures: BEDCA's line plus
   each reference source used, from `GET /api/reference/sources`.
9. **Patient form** (`PatientSelect.vue`): optional birth date and sex.
10. **Exchanges view** (Phase 4): a toggle to read each dish as exchanges (10 g CHO/protein/fat) for
    nutritionists who plan that way; the diabetes carbohydrate count is shown only when the
    diet is explicitly marked clinical.

Pure display helpers in `domain/` (e.g. `domain/rations.ts`) format ranges; they never compute
a ration count the backend didn't send.

---

## 8. Gaps the research did not close

- **`cucharadita` (cdta) and most household measures by food, for Spain, under an open licence.**
  DIAL's ~1,860-entry table is the best fit and is closed. USDA FDC is open but US (a US tsp is
  ~4.9 ml). Until a Spanish source is licensed, a `cdta` stays unmeasured unless the nutritionist
  confirms a USDA-labelled row. Action: ask UCM/Alce (DIAL), SENC, and FINUT.
- **Adult meal energy distribution** from a Tier 1 Spanish document. Only the school document
  states one.
- **0–3 years quantities**: needs the ASPCAT 2022 guide read in full, and the AEP 2018 document.
- **Older adults**: only SENC's combined column plus a "reduce individually" note. No Spanish
  older-adult ration table found.
- **Pregnancy, lactation, athletes, vegetarian/vegan, coeliac, renal, cardiovascular**: no Spanish
  ration or exchange tables were researched to the level of seedable values. Out of scope until
  Phase 5, and clinical ones never offered as general profiles.
- **Density** (FAO/INFOODS) and **yield** (Bognár/EuroFIR) for non-meat foods: permission needed.

---

## 9. Phases

**Phase 0 — permissions and verification (no code).**
- Re-read AESAN's legal notice (currently 404) and confirm class B for R2, R3, R11.
- Emails: SENC (use of Anexos 1.2–1.4 in a non-commercial tool), UCM/Alce (DIAL medidas caseras),
  FINUT (portion weight tables, not photos), FAO (density DB), Fundación para la Diabetes (HC
  table), CEU (TABULA). Record answers in `ref_sources.licence_text`.
- Verify every *(verify)* above against the original page; fill `page_ref`.

**Phase 1 — reference context and seed data.** V8 schema, CSVs for AESAN 2022, AESAN 2010 school,
RD 315/2025 frequencies, 5 al día 2019, BEDCA LanguaL column, the exchange **definitions**, group
mappings to `FoodCategory`, and SENC values only if permitted (else stored as citations without
figures). Read endpoints and sync. Tests.

**Phase 2 — ingredients that can be weighed.** Parser (measure-first, state, size, ranges),
`food_measure_id` + `state` on ingredients and extras (V10), `PortionScaler` measure step,
resolver attach-if-unambiguous, fix-up suggestions for measures, `countedByMeasure`, state
mismatch flag. Re-import `example-ui.xlsx` into a throwaway schema and report before/after counts
of `unmeasured`.

**Phase 3 — building from rations in the UI.** Create and import screens, profile picker, patient
birth date/sex (V9), composer in `DishEditPanel`, measure picking in `FoodLinkPanel`, ration strips,
meal energy bar, attribution footer. Ration accounting endpoint.

**Phase 4 — exchanges and equivalence bases.** Exchange counts per dish/day, alternatives by
nutrient basis, yield-factor suggestions (USDA meat first).

**Phase 5 — special populations.** Paediatric 0–3 (ASPCAT/AEP), older adults, pregnancy/lactation,
and clinical exchange systems behind an explicit "clinical" flag on the diet.

Each phase ends with: CLAUDE.md updated for the new context/migrations, the whole test suite green,
`pnpm build` clean, and the restart note for the backend on port 5000 when a migration lands.

---

## 10. Decisions needed from you

- **Q1. Commercial intent.** Is fdiet staying non-commercial? It already is, because of BEDCA. If it
  may become commercial, Phase 0 has to ask every source for commercial terms, not just
  non-commercial ones.
- **Q2. Default profile for adults.** Proposed: **AESAN 2022** (government, newest, class B). SENC
  2018 is selectable where permitted. For children, SENC 2018 bands (if permitted), else AESAN 2010
  school bands for lunch.
- **Q3. Can the nutritionist override a measure's grams per diet** (e.g. "my patients' cucharada is
  12 ml")? Proposed: yes, as a diet-scoped `ref_food_measures` row with source "criterio del
  profesional", shown as such, never mixed with published rows.
- **Q4. Adult meal distribution.** No Tier 1 Spanish value found. Leave it unset, let the
  nutritionist enter one per diet, or show the school distribution labelled as school-only?
- **Q5. Clinical exchanges (diabetes 10 g CHO).** In Phase 4 behind a clinical flag, or later?
- **Q6. Patient data.** Is adding birth date and sex to `patients` acceptable, given there is no
  authentication and every patient is readable by anyone who opens the app?

---

## 11. Sources

Spain
- SENC 2018 guide: https://www.nutricioncomunitaria.org/es/noticia/guia-alimentacion-saludable-ap · PDF https://www.fesnad.org/resources/files/guiaSENC.pdf
- AESAN 2022 report (AESAN-2022-007): https://www.aesan.gob.es/AECOSAN/web/nutricion/subseccion/recomendaciones_dieteticas.htm · PDF mirror https://www.riojasalud.es/files/content/ciudadanos/escuela-salud/cuida-tu-salud/alimentacion/profesionales/2022_AESAN_INFORME_recomend_dieteticas_sostenibles_AF.pdf
- AESAN/MEC 2010 school consensus: https://www.seghnp.org/documentos/documento-de-consenso-sobre-alimentacion-en-centros-educativos · PDF https://www.seghnp.org/sites/default/files/2017-05/ACE.pdf
- Real Decreto 315/2025: https://www.boe.es/buscar/act.php?id=BOE-A-2025-7659
- Russolillo et al. 2019 (5 al día), CC BY-SA 4.0: https://www.renhyd.org/renhyd/article/view/628 · PDF https://dialnet.unirioja.es/descarga/articulo/7379791.pdf
- BEDCA conditions of use: https://www.bedca.net/bdpub/UsoBD.pdf · AESAN composition page https://www.aesan.gob.es/AECOSAN/web/seguridad_alimentaria/subseccion/composicion_alimentos_BD.htm
- AESAN open data (branded composition 2022, links to NUTRIFEN, TABULA, BADALI): https://www.aesan.gob.es/datos-abiertos/alimentos-y-bebidas
- TABULA: https://ias.ceu.es/tabula-bbdd/presentacion/ · https://www.retailactual.com/noticias/20231212/base-datos-composicion-alimentos-bebidas
- DIAL: https://www.ucm.es/idinutricion/programa-dial · https://www.alceingenieria.net/infodial.htm
- Moreiras et al. 2022: https://www.edicionespiramide.es/libro/ciencia-y-tecnica/tablas-de-composicion-de-alimentos-olga-moreiras-tuni-9788436846577/
- FINUT photographic guide: https://www.finut.org/guia-fotografica-de-porciones-de-alimentos-consumidos-en-espana/
- ENALIA: https://www.aesan.gob.es/AECOSAN/web/seguridad_alimentaria/subdetalle/enalia.htm
- Russolillo & Marques exchange system: https://dialnet.unirioja.es/servlet/libro?codigo=768727 · https://www.elsevier.es/es-revista-actividad-dietetica-283-articulo-listas-intercambios-alimentos-confeccion-dietas-planificacion-menus-13142124
- UCM Carbajal, *Diseño de dietas* 2018 (teaching slides; exchanges, energy equivalents): https://www.ucm.es/data/cont/docs/458-2018-11-02-dise%C3%B1o-dietas-2018-WEB.pdf
- UCM INNOVADIETA directories: https://www.ucm.es/innovadieta/raciones · https://www.ucm.es/innovadieta/bibliografia-raciones · https://www.ucm.es/innovadieta/listas-intercambios · https://www.ucm.es/innovadieta/fotografias-raciones
- Fundación para la Diabetes, HC ration table: https://www.fundaciondiabetes.org/sabercomer/material/120/tabla-de-raciones-de-hidratos-de-carbono-ahora-impresas
- Hospital Sant Joan de Déu, HC rations: https://diabetes.sjdhospitalbarcelona.org/es/diabetes-tipo-1/raciones-hidratos-carbono/1000
- AEP 2018 complementary feeding: https://www.aeped.es/publicaciones/protocolos/recomendaciones-aep-sobre-alimentacion
- ASPCAT 0–3 guide: https://salutpublica.gencat.cat/ca/ambits/promocio/alimentacio-saludable/publicacions/publicacions-alimentacio-infants-joves/ · https://scientiasalut.gencat.cat/handle/11351/9237.3
- FEN older adults guide: https://www.fen.org.es/storage/app/media/pdfPublicaciones/guia-alimentacion-mayores-pdf-1.pdf
- BADALI (UMH): https://badali.umh.es/

Europe and international
- EFSA DRVs: https://www.efsa.europa.eu/en/topics/topic/dietary-reference-values · DRV Finder https://multimedia.efsa.europa.eu/drvs/index.htm · EFSA legal notice https://www.efsa.europa.eu/en/legalnotice
- EuroFIR recipe calculation / yield factors: https://www.eurofir.org/report-on-collection-of-rules-on-use-of-recipe-calculation-procedures-including-the-use-of-yield-and-retention-factors-for-imputing-nutrient-values-for-composite-foods/ · Bell et al. report https://toolbox.foodcomp.info/References/RecipeCalculation/Bell%20et%20al%20-%20Report%20on%20Nutrient%20Losses%20and%20Gains%20Factors%20used%20in%20European%20Food%20Composition%20Databases.pdf
- Carruba et al. 2023, IJERPH: https://www.mdpi.com/1660-4601/20/6/5230
- Vilela et al. 2018, pan-European picture book: https://www.cambridge.org/core/journals/public-health-nutrition/article/validation-of-a-picture-book-to-be-used-in-a-paneuropean-dietary-survey/5D9269967582D38805C1EAE1B4490767
- USDA FoodData Central (CC0): https://fdc.nal.usda.gov/ · https://catalog.data.gov/dataset/fooddata-central · field descriptions https://fdc.nal.usda.gov/docs/Download_Field_Descriptions_Oct2020.pdf
- USDA Table of Cooking Yields for Meat and Poultry, Release 2: https://www.ars.usda.gov/ARSUserFiles/80400535/Data/retn/USDA_CookingYields_MeatPoultry02.pdf · https://agdatacommons.nal.usda.gov/articles/dataset/USDA_Table_of_Cooking_Yields_for_Meat_and_Poultry/24660864
- FAO/INFOODS Density Database v2.0: https://www.fao.org/food-composition/tables-and-databases/detail/(global--2012)-fao-infoods-density-database---version-2/en · https://www.fao.org/4/ap815e/ap815e.pdf
- ADA/AND *Choose Your Foods* 2019: https://shopdiabetes.org/products/cyf-food-list-singles · https://www.eatrightstore.org/product-type/booklets-and-handouts/choose-your-foods-food-lists-for-diabetes-englishsingles--2019
- Dietary Guidelines for Americans 2025–2030: https://www.hhs.gov/press-room/historic-reset-federal-nutrition-policy.html · DGAC portion size chapter https://www.dietaryguidelines.gov/sites/default/files/2024-12/Part%20D_Ch%207_Portion%20Size_FINAL_508.pdf
- UK FSA NDNS appendix A (portion estimation, child atlases): https://www.food.gov.uk/sites/default/files/media/document/ndns-appendix-a.pdf
- SMAE (Mexico): https://fisiologia.facmed.unam.mx/wp-content/uploads/2019/02/2-Valoraci%C3%B3n-nutricional-Anexos.pdf
