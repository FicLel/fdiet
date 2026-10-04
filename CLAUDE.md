# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Gradle project (Java 21 toolchain, Spring Boot 4.1.0). On Windows use `gradlew.bat`, on Unix use `./gradlew`.

- Build: `gradlew.bat build`
- Run the app: `gradlew.bat bootRun` (serves on port 5000; runs the Flyway migrations first)
- Run all tests: `gradlew.bat test`
- Run a single test class: `gradlew.bat test --tests "com.fdiet.food.helpers.DataReaderTest"`
- Migrations: `gradlew.bat flywayInfo` / `flywayMigrate` / `flywayClean`

## Configuration

Every database setting comes from an environment variable. Copy `.env.example` to `.env` and
fill it in; `build.gradle` reads `.env` and passes it to `bootRun`, `test` and the `flyway*`
tasks. A real exported environment variable always wins over the `.env` entry.

`MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, and the optional
`SERVER_PORT`, `FOOD_CSV_PATH`, `BEDCA_CSV_PATH` and `COMPOSITION_DATA_PATH` (default
`reference-data/composition`).

## Architecture

fdiet is a Spring Boot backend organized per bounded context under `src/main/java/com/fdiet/<feature>/`.
The feature modules are `patient` (who a diet is written for), `food` (the product catalogue),
`diet` (the plan built on it), `alternative` (what one food may be swapped for) and `journal`
(what the patient thought of the plan and what they ate beside it) and `reference` (rations, household
measures and recommendations from published guidelines, each row with its source); `common` holds
the shared transport types.

**No security layer.** No Spring Security, no JWT, no authentication anywhere — every route is
open. This is intentional for the current stage.

**A patient is a name, not an account.** `patients` exists so a diet, and the journal hanging off
it, can say *whose* it is — nothing more. It authenticates nobody and hides nothing: every
patient's week is readable and writable through the same endpoints as every other's, and the UI's
patient selector is a control over which data is on screen rather than a login. Ownership is now
recorded, so when accounts arrive the endpoints need to stop trusting the `patientId` in the URL,
and that is the only step left — the column they would need is already there.

The journal still hangs off the diet rather than off a patient directly, and correctly: a diet
belongs to exactly one patient, so `dish_scores.diet_id` already says who wrote the score.

### Layering rules

`controller -> service -> repository`, wired by constructor injection everywhere.

- The controller is dumb: it forwards arguments to a service and returns what comes back. It
  builds no `Pageable`, holds no logic, and never sees an entity.
- DTOs cross the layer boundaries. `PageDto` wraps paged results so Spring Data's `Page` stays
  inside the service layer.
- Each table has exactly one owning service, and only that service touches its repository.
  When a service needs another table it calls that table's service — see
  `SubCategoryService` reaching categories through `CategoryService`, never through
  `CategoryRepository`. Service-to-service calls may pass entities (`entitiesById()`), because
  that is one layer talking to itself rather than a layer boundary.

### `food`

- `controller/FoodController` — `@RestController` at `/api/food`:
  - `GET /api/food?name=&page=&size=` — page of items, `name` matches the commercial name.
  - `GET /api/food/{id}` — one item, 404 through `FoodItemNotFoundException`.
  - `POST /api/food/sync` — imports fooddata.csv.
- `service/FoodItemService` — owns `food_items`: search, lookup by id, and the row import.
- `service/FoodImportService` — owns no repository. Parses the CSV into `FoodCsvRowDto` and
  hands the rows to the service that owns each table. Column positions live here as constants.
- `service/CategoryService`, `service/SubCategoryService` — own the two lookup tables.
- `repository/` — Spring Data interfaces, plus the `FoodItemBatchRepository` fragment whose
  `EntityManager`-based impl keeps the bulk insert in the repository layer.
- `mapper/` — `@Component` mappers between entities and DTOs.
- `model/` — JPA entities `FoodItem`, `Category`, `SubCategory` → `food_items`,
  `food_category`, `food_subcategory`.
- `helpers/DataReader` — hand-rolled RFC 4180 CSV parser (quoted fields, embedded
  delimiters/line breaks, doubled quotes, leading BOM). `foodData()` reads the path in
  `fdiet.food.csv-path`; `read(Path)` takes any CSV of the same dialect, which is how the
  composition database is read without a second parser.

### The two halves of the catalogue

`food_items` and `composition_foods` are both foods and they answer different questions.

| | `food_items` | `composition_foods` |
| --- | --- | --- |
| what | branded commercial products | generic foods, with fdiet's Spanish names |
| source | fooddata.csv, keyed on EAN | CIQUAL 2025 + BLS 4.0, keyed on `(source, source_code)` |
| size | ~100 k rows | ~10,600 rows, 123 named in Spanish |
| a name | `BEKIND BARRA CEREAL CARAMELO ALMENDRA Y SAL` | `Lechuga`, `Pollo, pechuga, plancha` |

A diet says `lechuga (80 gr)`, so **the composition foods are the half that answers**: an
import of example-ui.xlsx matches 0 of 210 ingredients against `food_items` and 144 against the
Spanish crosswalk of `composition_foods`. The branded catalogue is still there for the days a diet
names a product outright. Since FD-033 phase D (`V17`/`V18`) every recipe ingredient, journal
extra, reference row, alternative and ration count points at `composition_foods`; `bedca_foods`
below is still loaded and served at `/api/bedca`, but **nothing matches to it any more** and
phase E removes it.

### `bedca_foods` — the retired composition database (removed in FD-033 phase E)

Nothing outside `/api/bedca` reads it since phase D. `recipe_ingredients.bedca_food_id` and
`extra_foods.bedca_food_id` are NULL on every row and no entity maps them; `bedcaFoodId` in any
request is a 400 (`common/helper/RetiredFields`, `@Null` on the retired field — a field Jackson
simply ignored would let an old caller's match vanish without a word).

- `controller/BedcaController` — `@RestController` at `/api/bedca`:
  - `GET /api/bedca?name=&page=&size=` — page of foods. No `name`: alphabetical. With one, it is
    a **ranked word search** (FD-020): the term is reduced by `NameMatcher` (accents, plurals,
    quantity and serving words dropped) and every food sharing at least one word shows — most
    words shared first, then the `suggest` order (share of the food's name covered, then name).
    A food that shares no word but contains the term as typed (`lechu` → `Lechuga`) comes last,
    so a box being typed into never goes blank mid-word. Ranked from the in-memory suggestion
    index; the page's foods are one `findAllById`. `pan de molde` puts both `…, de molde,
    tostado` breads first; `jamón york` lists the `Jamón …` foods (no synonyms yet).
  - `GET /api/bedca/{id}` — one food, 404 through `BedcaFoodNotFoundException`.
  - `POST /api/bedca/sync` — imports bedca_foods.csv; safe to re-run.
- `service/BedcaFoodService` — owns `bedca_foods`: search, lookup, the batched
  `entitiesByName` / `entitiesByIds` the diet resolves through, the suggestion ranking, and
  the sync's writes.
- `service/BedcaImportService` — owns no repository. Finds its columns **by header name**, not
  by position: the file is 111 columns wide and 14 of them are wanted.
- `service/NutritionService` — the unit arithmetic, and nothing else. A figure whose unit it
  does not know comes back null rather than as a number in the wrong scale.
- `helpers/NameMatcher` — reduces a food name to the words that carry it (accents, plurals,
  quantities and serving words dropped) and scores the overlap. It **ranks and never decides**.
- `model/BedcaFood`, `model/NutrientValue`, `model/Nutrient` — the entity, the embeddable
  value+unit pair, and the enum that names the fourteen components in one place so the
  importer, the mapper and the DTO all loop over it instead of repeating them.

**Values are stored exactly as published, each with its own unit.** Energy is kJ for 947 of
the 957 foods and kcal for 8; carbohydrate, fibre and water each have a stray milligram row.
Reading a value without its unit is a wrong number, which is why the table carries a `*_unit`
column beside every value. Anything derived — kcal from kJ, a portion scaled off the per-100 g
figure — is computed on read and never written back. The terms of use require this and require
the attribution to appear wherever the figures are shown; see **BEDCA-ATTRIBUTION.txt**, and
note that the data is **non-commercial** without AESAN's authorisation.

Only the fourteen components a diet is read by are stored. The other 33 stay in the CSV;
adding one is a constant in `Nutrient`, a field on `BedcaFood` (and `CompositionFood`) and a
column pair in a migration. `Nutrient` reads and writes through `model/CompositionFigures`, the
fourteen value+unit accessors both composition entities implement, so `NutritionService.per100g`
and the mappers serve BEDCA, CIQUAL and BLS alike.

### `composition_foods` — the open composition tables (FD-033 phase B, `V15`)

**CIQUAL 2025 (ANSES) and BLS 4.0 (Max Rubner-Institut), both CC BY 4.0, in one table.** It is
BEDCA's open replacement: the reference rows point at it since phase C (`V16`), and recipe
ingredients and journal extras since phase D (`V17`/`V18`, below). One table rather than two, so
every consumer points at one id column.

- `source` (`CIQUAL` | `BLS`, `model/CompositionSource`, which also carries each licence's
  attribution) + `source_code` as published (CIQUAL `alim_code`, BLS `C131000`);
  `uk_composition_foods_source_code` is the key a sync writes over, so ids never change. **Every
  food carries its source** — CIQUAL's protein is nitrogen × Jones factor, BLS's × 6.25, and BLS's
  energy is its own formula — and `CompositionFoodDto` sends `source`, `sourceLabel` and
  `attribution` with every food.
- **Values as published, with units**, like `bedca_foods`. Energy is the source's **kcal** figure
  (CIQUAL's Regulation (EU) 1169/2011 column, BLS `ENERCC`), so no kJ→kcal factor is applied to it.
  **A qualified value (`traces`, `< 0,2`, `<LOQ`, `<LOD`, `TR`, `-`) is NULL**, never 0; the original
  text is not copied beside it — the committed upstream file is the record of what was written.
- **Energy can be missing**: 145 CIQUAL foods publish no number for it (143 `-`, 2 `traces`), listed
  in `reference-data/composition/composition-es/ciqual_no_energy.csv` (FD-037) and flagged
  `energyPublished: false`. Nothing downstream may assume energy: `AlternativeService` already gives
  such a food no equivalent weight by energy and still ranks it on its other figures (tested).
- **fdiet's crosswalk columns** — `name_es`, `name_aliases`, `name_preferred`, `name_reviewed`,
  `edible_portion`, `edible_portion_fdc_id` — come from `composition-es/links.csv`, not from either
  source, and are NULL on every food it does not name. `name_es` is written **head first, BEDCA
  style** (`Pollo, pechuga, plancha`), so `FoodCategoriser` (121 of the 123 names) and the
  `FoodState` reader read it. **CIQUAL answers first; a BLS row is used where CIQUAL has no
  same-food equivalent, or only BLS has the state written or an energy figure.** A name two rows
  claim goes to the one marked preferred; a name shared without exactly one preferred stops the
  sync before anything is written (`helpers/NameIndex`, the same index the lookup uses).
- **Every crosswalk row starts as a machine prefill (`reviewed = false`)** and a person approves it; the
  122 rows of FD-033 phase B were approved by the project owner on 2026-10-03; the one row phase C
  added (CIQUAL 13716, piña en su jugo) was approved by the project owner on 2026-10-03.
  Scope today: the "Dieta 1" foods of example-ui.xlsx and the BEDCA foods referenced on 2026-10-03
  (123 rows); the rest is FD-036.
- **`edible_portion` = 1 − refuse/100 of the USDA SR Legacy food in `edible_portion_fdc_id`**
  (CC0; `usda-sr-legacy/refuse.csv` is the extract, and a test checks every row against it). NULL
  where no SR Legacy food fits (5 rows) — and NULL still refuses a gross weight, as with BEDCA.
- **Matching is exact**: `ICompositionFoodService.entitiesByName` answers a Spanish name or alias,
  case and accents ignored, from an in-memory index of every row (one query, dropped on sync); the
  index is built by `CompositionIndexRow.nameIndex`. Re-importing example-ui.xlsx matches **144 of
  210** ingredients outright (BEDCA: 41, the floor) — `ExampleDietCompositionMatchTest` measures it
  through the real `FoodResolverService` on every build.
- **Suggestions and alternatives read Spanish names only.** `suggest(text, limit)` ranks the
  crosswalked foods' Spanish words in memory (`CompositionSuggestionDto`: id, name, `source`,
  `sourceLabel`, score) and `entitiesNamed()` hands `com.fdiet.alternative` the crosswalked foods in
  one query. A food without a Spanish name can still be found by search and picked by id, but it has
  no family, no state read off its name and no suggestion: those are judgements about Spanish names.

The classes, all in `food/`: `controller/CompositionController`; `service/CompositionFoodService`
(owns the table: search, lookup, the batched `entitiesByName` / `entitiesByIds`, the sync's
writes), `service/CompositionImportService` (owns no repository: reads both tables and the
crosswalk outside the write transaction, joins them in memory, one `storeAll`),
`service/CompositionStartupSync`; `helpers/CiqualTableReader`, `helpers/BlsTableReader`
(`ICompositionTableReader`, columns found **by header**), `helpers/SheetStreamReader` (POI's
streaming reader: BLS is 7,140 × 418 cells, too big for the in-memory workbook),
`helpers/CompositionLinkReader`, `helpers/NameIndex`; `repository/CompositionFoodRepository` with
the `CompositionFoodUpsertRepository` fragment — one JDBC `INSERT … ON DUPLICATE KEY UPDATE` in
batches of `fdiet.composition.batch-size`, because Hibernate cannot batch identity inserts.

**Loading.** At startup, only when the table is empty (`fdiet.composition.sync-on-startup`, on by
default): ten thousand rows out of 15 MB of spreadsheets is too slow to repeat on every start, unlike
the reference CSVs. After changing a snapshot or the crosswalk, `POST /api/composition/sync`. The
composition startup sync runs before the reference one (`@Order`), because reference rows name
composition foods and a fresh database can only key them once the foods are in.

**Who points at it.** The reference rows (phase C, `V16`): `ref_rations` and `ref_food_measures`
(published rows, diet criteria, global criteria) name a food by `composition_food_id`, and the
reference CSVs by `composition_source,composition_code`; `ICompositionFoodService.idsByKey` resolves
the stable keys from the same in-memory index (no query once it is built). Recipe ingredients and
journal extras (phase D): `recipe_ingredients.composition_food_id` and
`extra_foods.composition_food_id` (FK, indexed, no `ON DELETE`; a check keeps at most one of it and
`food_item_id`).

**The phase D reset and re-match (`V17` + `V18`, applied to the dev DB 2026-10-03).** `V17` added the
columns and set every `bedca_food_id` to NULL; no BEDCA id was carried over (decisions 2, 19). `V18`
is a **Java** Flyway migration (`src/main/java/db/migration/`) that re-matched every row without a
food by **exact Spanish name or alias, once more without size words** — `diet/helpers/ExactNames` over
`CompositionIndexRow.nameIndex`, the very rule a fresh import goes through — and released a household
measure whose row names another composition food. A migration, not a startup step or an endpoint,
because it must run exactly once per database, before anybody reads a week, and Flyway's history is
the record that it did; an endpoint left behind could re-match archived weeks silently. On a fresh
database it finds nothing to do. The Gradle `flyway*` tasks compile first and read
`classpath:db/migration` beside the SQL folder, so they resolve `V18` too (without that, `flywayInfo`
listed it as "Future" and a fresh `flywayMigrate` would have skipped it, leaving a hole that fails
validation once a later migration is applied). Before/after per diet:
`product/reports/FD-033-D-before-after.md` (452 of 959 rows matched by exact name, BEDCA had 158).

### Importing

`Category` and `SubCategory` use the ids that ship with the CSV (`IdCategoria`,
`IdSubcategoria`) as assigned primary keys — subcategory ids are unique across the whole file.
Food items are keyed on their EAN, which is unique in the CSV, so `POST /api/food/sync` skips
rows already stored and can be re-run safely.

The current fooddata.csv only fills 19 of the `food_items` columns; the rest (market share,
manufacturer, subbrand, portion size, sodium, fibre, …) are kept for richer exports of the same
dataset and stay null after an import.

### `patient`

Who a diet is written for. It owns `patients` — an id, a name, a free note, a creation stamp, and
an optional birth date and sex (`V9`) — and that is the whole of it. The birth date only *suggests*
which reference profile a new diet is read against (`Patient.ageInMonths`); nothing is decided by it.

- `model/Patient`, `repository/PatientRepository`, `service/PatientService` (+ `IPatientService`),
  `mapper/PatientMapper`, `controller/PatientController` at `/api/patients`, and the two
  exceptions.

**The dependency runs one way: a diet points at its patient, and a patient row holds no diets.**
`Patient` has no `diets` association and `PatientService` never calls into `com.fdiet.diet`; the
diet service calls `IPatientService.entityById` for the row it has to point at, which is a service
handing an entity to a service — one layer talking to itself. That is also why "which diet is each
patient on" is `GET /api/diets/current` and not a field on `PatientDto`: the answer belongs to the
module that owns `diets`, and putting it here would turn the arrow around.

**The name is the whole identity while there are no accounts**, so `uk_patients_name` forbids two
of them — a list with two `Victor`s names nobody. The collation is case- and accent-insensitive,
which is how a person reading the list compares. `PatientService` checks it first for a message
somebody can act on and leaves the index underneath for the write that check races with.

Deleting a patient who still has diets is **refused**, not cascaded: `fk_diets_patient` has no
`ON DELETE`, and the service turns the resulting integrity violation into a 400. The weeks written
for somebody are the record of them, and dropping a name should not drop the record. The check is
the foreign key itself rather than a query, because `diets` is another context's table. The
refusal says where to go instead: delete the patient's diets one by one (`DELETE /api/diets/{id}`,
from the patient's list of diets), then the patient. It is in English like every other refusal;
the UI answers that 400 with its own Spanish text naming the same way out (the "Dietas" list beside
the patient in the builder's selector).

`V7` seeds one patient, `Victor`, and hands every diet that already existed to them — they were all
written for one person. The row is seeded on an empty database too: a screen that picks a patient
before it can do anything needs one to exist before the first diet does.

### `diet`

Every diet belongs to one patient, and there is **one active diet per patient** plus a record of
their archived ones; nothing generates a week, the nutritionist writes it. The module keeps two
shapes of the same idea apart:

- `model/` — the JPA entities `DietPlan`, `PlannedMeal`, `PlannedDish` → `diets`, `diet_meals`,
  `diet_dishes`, and `Recipe`, `RecipeIngredient` → `recipes`, `recipe_ingredients`. Named
  `Planned*` so the bare `Diet` and `Meal` stay with the in-memory classes and no file ever imports
  two of either.
- `domain/` — `Diet` and `Meal`, plain in-memory classes that validate a submitted week (a day
  once per diet, a meal once per slot) and hand it back ordered by `DayOfWeek` / `MealType`
  through their `EnumMap`s. Entities read from the database get their ordering from here rather
  than from an `@OrderBy` on the days.
- `repository/DietRepository` — `findFirstByPatientIdAndStatus(patientId, ACTIVE)` is the main
  question: the diet in force now, for one person.
  `findByPatientIdAndStatusNotOrderByStartedOnDesc` is that patient's history, and
  `findByStatusOrderByPatientNameAsc(ACTIVE)` is everybody's diet in force in one query. Every
  query is scoped by patient except the two that address a diet by its own id — an id is already
  somebody's.

Two invariants live in the schema, not only in the code:

- `diets.active_flag` is a generated column (1 while `status = 'ACTIVE'`, NULL once archived) and
  `uk_diets_active` spans **`(patient_id, active_flag)`**. MySQL allows a unique index to hold any
  number of rows with a NULL in them, so every patient keeps all of their archived diets and no
  patient can hold a second active one. **The entity does not map the generated column** —
  Hibernate validates only mapped columns, so an insert can never write it.
- `uk_diet_meals_slot (diet_id, day_of_week, meal_type)` restates `domain/Meal`'s one-meal-per-slot
  rule at the database level.

`DietPlan.patient` is a `@ManyToOne`, a mapping rather than a layer crossing — the same way an
ingredient maps the food it was matched to, and for the same reason: `DietDto` and `DietSummaryDto`
carry `patientName` beside `patientId` so a heading never fetches a patient to write itself. The
repository's entity graphs fetch it, since every one of those rows is read back saying whose it is.

**A diet never changes hands through an edit.** `PUT /api/diets/{id}` requires the body's
`patientId` to be the one the diet already belongs to and refuses anything else, because moving a
diet would take the patient's journal with it — the scores and off-plan entries hang off the diet
id — and the week would arrive under a new name carrying somebody else's opinion of it.
`POST /api/diets/{id}/copy` is the answer, and it leaves both diets alone.

### Recipes (`V13`)

**A plate is a description and a recipe, and the two are not related by text.** `diet_dishes.name`
is what the patient reads — "Huevos revueltos" — and nothing reads food out of it. The food is the
plate's `recipe_id`: a `recipes` row holding the ingredients **for one serving**, the text they were
written as (`raw_text`) and free-text preparation (`steps`). `diet_dishes.servings` (default 1)
multiplies the recipe for that plate; nothing scaled is stored — `domain/Serving` pairs an
ingredient with its plate's servings and `DietNutritionService` / `DietRationService` multiply the
portion factor on read (the counts are never scaled). `recipe_id` null is a description-only plate
("Comida libre"). The patient sees the recipe — ingredients at the plate's servings, and the
steps — by pressing the plate.

Two kinds of recipe share the table:

- **Library** (`library = TRUE`) — shared and **linked**: every plate that points at one reads it, in
  every patient's week, so an edit through `/api/recipes` reaches all of them at once, **without a
  publish**. `GET /api/recipes/{id}/usage` says whose weeks that is, and the UI shows it before a
  save. Names are unique through `library_key`, a generated column (the name while in the library,
  NULL otherwise) under `uk_recipes_library_name` — the `active_flag` trick again. Deleting one
  still on a plate is refused (`fk_diet_dishes_recipe` has no `ON DELETE`), not cascaded.
- **Private** (`library = FALSE`) — one plate's own, written in its cell. It is replaced with the
  week (the publish gates it, like everything else) unless the plate names it by `recipeId`, which
  keeps it as stored. After a `PUT`, the private recipes the old week held and the new one no longer
  names are deleted. A copied diet shares library recipes and clones private ones.

**A library recipe is never weighed by one diet's own measure** (`ref_food_measures.diet_id`): that
row goes with its diet, and the shared recipe would go quietly unweighed everywhere. Saving such an
ingredient to the library, or PATCHing one to it, is a 400; library ingredients are chosen measures
without any diet's criteria, and `PUT …/measures` attaches only to private recipes. The
nutritionist's **global** criteria (`V14`, see `reference`) belong to no diet, so they do weigh
library recipes, exactly as they weigh every week.

`service/RecipeService` (`IRecipeService`) owns both tables and everything about an ingredient:
reading text, matching (the batched `foodsOf` / `measuresOf`), the fix-up list and the PATCH. The
diet service hands it the recipe ids its plates serve (`PlannedDishRepository.recipeIdsOf`), so a URL
naming one diet never reaches another's ingredients. It lives in `diet/` rather than a context of
its own because it needs the parser and resolver, which are the diet's.

`V13` gave every existing plate a private recipe (its name, its `raw_text`) and renamed
`diet_ingredients` to `recipe_ingredients` re-parented onto it, so every match survived; the old
foreign keys keep their `fk_diet_ingredients_*` names. The import still reads each cell into a
private recipe, named by the text or the row; it never links a library recipe by name.

`RecipeIngredient` carries `raw_name` — what the diet calls the food — and *may* point at one of
the two catalogues: `composition_food_id` for a CIQUAL / BLS food, the usual match, or
`food_item_id` for a branded product. **Both are nullable, on purpose, and only one is ever set.**
Both null means "not matched yet": the ingredient is stored exactly as written and matched later
through `PATCH /api/diets/{id}/ingredients/{ingredientId}`, because dropping it would silently
lose part of the week. Nutrition figures for an ingredient are only available once it is matched.
The entity associations are mappings, not layer crossings — when the diet service needs food
*data* it goes through `IIngredientFoodService` (which asks `ICompositionFoodService` /
`IFoodItemService` and the resolver in batches, and ranks the fix-up suggestions), never a food
repository. `RecipeIngredient.compositionFoodId()` is the column's id, compared with the food a
reference row or a measure criterion names.

`Recipe` carries `raw_text` for the same reason one level up: **the ingredients as they were written**.
Reading a sentence into a name and quantities cannot be undone — `MealTextParser` keeps one
quantity per ingredient and no brackets in a name, so `Tostada (60 gr) con tomate (80 gr)` puts
back together as `Tostada con tomate (80 gr) (60 gr)`, the same food and a moved weight. Since
`PUT /api/diets/{id}` replaces the whole week, an editor changing one cell has to send the other
sixty-nine back; without the sentence it could only send rebuilt ones, and a nutritionist would be
editing a rewrite of what they typed. **It is nullable and never reconstructed**: null means "what
was written is not known", which is the truth for every recipe migrated from a dish stored before `V5`, and an invented
original would make the loss permanent instead of visible.

### Nutrition

Once an ingredient is matched, `service/DietNutritionService` scales the food's per-100 g figures
to the quantity written and adds them up; the totals hang off `MealDto`, `DietDay` and `DietDto`
as a `NutritionSummaryDto`. The composition figures are converted by the food module's
`INutritionService` (it knows what a kilojoule is); the portion arithmetic is
`helpers/PortionScaler` (how much of a food a diet prescribes is the diet's business). It owns no
repository and reads only what the caller already loaded, so a whole week is totalled without one
extra query.

**A total always travels with its counts.** Every ingredient lands in exactly one of `counted`,
`unmatched` and `unmeasured`, which sum to `ingredients`. Most of a freshly imported week is
unmatched, and a total over a third of the ingredients looks exactly like a total over all of
them unless something says otherwise. `unmeasured` is an ingredient that *is* matched but is
written in a unit nothing can weigh — `PortionScaler` refuses to guess what a spoonful weighs.
Its one assumption is that a millilitre is a gram, which a diet's liquids (water, broth, milk,
juice) are within a few percent of; for olive oil it overstates by about 9 %, and that is kept so
`10 ml` and `1 cucharada sopera` of oil never disagree.

**A quantity weighed in another state than its food is never totalled** (FD-052).
`RecipeIngredient.isStateMismatch()` — the written `state` and the state read off the matched
composition food's Spanish name on opposite sides of cooking (`55 g en seco` against `Lenteja,
cocida`) — puts the ingredient in `unmeasured`, and `NutritionSummaryDto.unmeasuredByState` says how
much of `unmeasured` that is; the counts still sum to `ingredients`. `DishIngredient` carries
`stateMismatch: true` and, where a published cooking yield fits, `yieldHint` (≈ 108 g at 72 % for
150 g raw breast against grilled breast). The yield is **offered, never applied**, with or without
one: pricing the written grams at the food's state is a wrong number (55 g dry lentils priced as
cooked is about a third of their energy), and converting by a yield would be a choice the text did
not make. The grams are still read (`edibleGrams`), so the hint can say what they come to.

**A household measure weighs only through a row that says so.** `recipe_ingredients.food_measure_id`
(`V10`) points at a `ref_food_measures` row — published (`1 cucharada sopera` of olive oil, 10 ml,
AESAN 2022), the diet's own criterion, or the nutritionist's global criterion (`1 huevo mediano = 58 g`)
— and `PortionScaler.weigh` uses it: the row's point weight
divided by its count (`3 Uds. medianas = 180 g` is 60 g each), cut to the edible part by the food's
`edible_portion` when the row is a gross weight (and refused when that fraction is unpublished). A
range (`53–63 g`) weighs nothing, and a measure weighs only the unit it measures. `countedByMeasure`
says how much of `counted` rests on one.

**A measure is either a person's pick or the rule's choice, and the row says which** (FD-054, `V19`):
`recipe_ingredients.measure_picked` / `extra_foods.measure_picked`, carried as `measurePicked` on
`DishIngredient` and `ExtraFoodDto`. Picked = a person chose it: a PATCH sending `foodMeasureId`, the
composer's measure (`compose` with `foodMeasureId`), a parse `keep` entry carrying `foodMeasureId`, an
extra logged with one. A pick is kept through every re-read, publish and criterion while it still
weighs the food and unit (FD-039); the rule's choice is made again every time — on a `PUT`, a parse,
a PATCH that names no measure, and **at once in every week, archived ones included, when a diet or
global criterion it now reaches is written** (below). The flag means something only beside a measure
(`measurePicked()` on both entities); the measure's FK sets NULL on delete and MySQL refuses a CHECK
there. On the way in, `DishIngredient.measurePicked` left out beside a `foodMeasureId` counts as picked,
so a caller from before FD-054 loses nothing; `false` hands the measure back to the rule.

**The patient reads the unit, never works it out.** `DishIngredient` and `ExtraFoodDto` carry
`unitWording: {singular, plural, sizeInName}` — `reference/domain/UnitWording`, derived on read from
`name`, `unit` and `size`, never stored, ignored when a body sends it back. For a household measure it
is `HouseholdMeasure.written` in both numbers with the size agreeing (`unidad mediana` /
`unidades medianas`, `vaso pequeño` / `vasos pequeños`), so the gender and plural of a measure stay in
Java; a weight, a volume or a word the vocabulary does not know is the unit as stored in both. The
reader picks by the quantity served (servings × quantity). `sizeInName` is true when the name already
carries a size word (`1 kiwi mediano` keeps the name `kiwi mediano`); the size is then left out of the
wording, so quantity + unit + name says it once.

### Interfaces and injection

Every class in `diet/`, `alternative/` and `patient/` is injected through an interface
(`IDietService`, `IDietComposeService`, `IRecipeService`, `IIngredientFoodService`, `IDietMapper`, `IMealTextParser`, `IPortionScaler`, `IDietNutritionService`,
`IAlternativeService`, `IFoodCategoriser`, `INutritionSimilarity`, `IPatientService`,
`IPatientMapper`, `IReferenceService`, `IMeasureCriterionService`, `IReferenceMapper`, `IDietRationService`,
`IMeasureResolverService`, `IDietMeasureService`, …), as are the food services
they depend on: `IFoodItemService`, `IBedcaFoodService`, `INutritionService`, `INameMatcher`,
`IBedcaImportService`, `IBedcaFoodMapper`, `ICompositionFoodService`, `ICompositionImportService`,
`ICompositionFoodMapper`, `ICompositionTableReader`, `ICompositionLinkReader`, `ISheetStreamReader`. The older `food/` classes
(`FoodItemService`'s siblings, `FoodImportService`) still use their concrete types. The
repositories are Spring Data interfaces already.

### Endpoints

`CompositionController` at `/api/composition` — CIQUAL 2025 and BLS 4.0 (see `composition_foods`):

- `GET /api/composition?name=&page=&size=` — without `name`, by source and English name; with one,
  foods whose Spanish name or aliases share its words first (most shared first), then any food whose
  Spanish, English or original name contains the text as typed (`lettuce`, `laitue`), so a food the
  crosswalk does not name yet is still found. Each food carries `source`, `sourceLabel`,
  `attribution`, `energyPublished`, and its figures as published and converted.
- `GET /api/composition/{id}` — one food, 404 through `CompositionFoodNotFoundException`.
- `POST /api/composition/sync` — loads both snapshots and the crosswalk; safe to re-run (upsert on
  `(source, source_code)`, ids kept). Answers with counts per source, `linked`, `linksUnmatched`,
  `withoutEnergy` and **both attribution strings**. A crosswalk that gives one name to two foods
  without exactly one preferred is a 422, and nothing is written.

`PatientController` at `/api/patients` — the caseload is a handful of rows, so the listing is not
paged:

- `GET /api/patients`, `GET /api/patients/{id}` — everybody, by name; or one of them.
- `POST /api/patients` (201) — `{name, notes?, birthDate?, sex?}`; the name must not be one somebody
  already holds. `sex` is `FEMALE` or `MALE`.
- `PUT /api/patients/{id}` — rewrite every field; one left out is cleared.
- `DELETE /api/patients/{id}` (204) — a patient with no diets. One who still has diets is a 400.

`DietController` at `/api/diets` — plain REST, no HATEOAS envelope, pagination as query
parameters. `patientId` is required wherever the question is about a person, and absent wherever a
diet is addressed by its own id:

- `POST /api/diets` — store a week as that patient's diet in force (201); `patientId` in the body.
- `PUT /api/diets/{id}` — replace a diet's whole week. The body's `patientId` must be the patient
  the diet already belongs to. Each dish is `{name, servings?, recipeId? | recipe?}`: `recipeId`
  names a library recipe or one of this diet's own private ones (kept as stored); `recipe`
  (`{rawText, steps, ingredients}`) writes a new private one; both is a 400, neither is a
  description-only plate.
- `POST /api/diets/{id}/copy` (201) — `{patientId, name?, startedOn?}`. Writes the same week again
  for another patient: its days, dishes, servings, private recipes (cloned, `raw_text` and every
  food match already made) and library links (shared). It becomes
  their diet in force, archiving what they were on; the source is untouched and the **journal is
  not copied**.
- `DELETE /api/diets/{id}` (204; unknown id 404) — any diet, active or archived. One `DELETE` of
  the `diets` row; the schema cascades its meals, dishes, scores, extras and own measure criteria
  (`ON DELETE CASCADE`, V2/V6/V8). The service reads the recipe ids its plates serve first and
  deletes the **private** ones afterwards (`IRecipeService.deletePrivate`); **library** recipes and
  global criteria are untouched. Deleting the diet in force leaves the patient with **no diet in
  force** — no archived diet is reactivated; which one to resume is a person's decision.
  `GET /api/journal/{dietId}/counts` answers what the confirm warns about.
- `GET /api/diets/active?patientId=`, `GET /api/diets/{id}` — the week, ordered by day and slot.
- `GET /api/diets/current` — every patient's diet in force, without their weeks: who is on a diet
  right now, in one query. This is the board the UI's patient selector is drawn from.
- `GET /api/diets?patientId=&page=&size=` — that patient's archived diets, without their weeks.
- `GET /api/diets/{id}/ingredients?resolved=false&suggest=true&page=&size=` — the fix-up list:
  the ingredients of every recipe the diet's plates serve, library ones included.
  `suggest=true` attaches the best CIQUAL / BLS candidates (`{compositionFoodId, name, source,
  sourceLabel, score}`, by Spanish name) to each unmatched ingredient, ranked, costing no query.
  Each ingredient carries `compositionFoodId`, `matchedName` and `matchedSource`.
- `PATCH /api/diets/{id}/ingredients/{ingredientId}` — match one ingredient to a food
  (`compositionFoodId` or `foodItemId`, not both — matching to one releases the other;
  `bedcaFoodId` is a 400), or correct its
  name, quantity or unit. Fields left out are left alone. On a library recipe's ingredient the
  change reaches every plate that serves it.
- `POST /api/diets/parse` — reads recipe text (`text`, optional `slotName`) into a recipe with
  its ingredients matched and priced, **storing nothing**. It exists so the editor never has a
  parser of its own: a second implementation would drift from the importer, and the two would then
  disagree about what the same line of text means. Optional `keep:
  [{name, compositionFoodId | foodItemId, foodMeasureId?}]` (FD-048) carries the matches the editor
  already holds, so a re-read after an edit does not undo them: an ingredient read under a kept name
  — compared by `Texts.key`, the crosswalk index's key (case, accents, whitespace ignored); the same
  name twice is paired in order — keeps that food instead of the resolver's answer, even a food with
  no Spanish name or a non-preferred row, and the recipe is priced with it. Its measure is handed on
  as the pick and re-validated as FD-039 does, so it is dropped once the unit no longer is the word it
  measures. **`foodMeasureId` in a keep entry means "a person picked this"** (FD-054): send it only for
  a picked measure; the ingredient comes back with `measurePicked: true` while it is kept. A measure
  the rule chose is left out of the entry, so the rule chooses again. An edited name keeps nothing. Both ids, or neither, in one entry is a 400, as is
  `bedcaFoodId`. `diet/helpers/KeptMatches`, O(n + k), no query of its own: kept foods go into the
  same batched `entitiesByIds` and are never sent to the resolver.
- `POST /api/diets/import` — multipart `file`, required `patientId`, optional `sheet`, `name`,
  `startedOn`, `referenceProfile`, `clinical`.
- `PATCH /api/diets/{id}` — `{name?, referenceProfileCode?, clinical?}` without sending the week.
  A blank `referenceProfileCode` takes the profile off.
- `POST /api/diets/compose` — `{compositionFoodId, grams | foodMeasureId + count, state?, dietId?}` →
  the text fragment to append to a cell (`Lenteja, seca, cruda (60 g en crudo)`) and what the parser
  reads back from it, pinned to the food asked for (so a food with no Spanish name still comes back
  matched). The editor's "añadir por raciones" goes through this, so it still has no parser.
  Answered by `service/DietComposeService` (`IDietComposeService`), which owns no table and asks
  `IDietService` for the diet's profile.
- `GET /api/diets/{id}/rations?profile=` — the week counted in rations (see `reference`).
- `GET /api/diets/{id}/measures`, `PUT /api/diets/{id}/measures`, `DELETE /api/diets/{id}/measures/{measureId}`
  — the diet's own measure criteria (`{measure, size?, compositionFoodId, grams | ml, note?}`). A PUT
  re-chooses the measure of every ingredient of the diet's private recipes and every extra of its
  journal matched to that food, written in that measure, whose measure nobody picked (FD-054), and
  answers `{measure, attached, reweighed: {ingredients, extraFoods}}` — `attached` is the total
  re-weighed. Answered by `service/DietMeasureService` (`IDietMeasureService`), split off
  `DietService`; it owns no table.

`RecipeController` at `/api/recipes` — the library only; a private recipe is reached through its plate:

- `GET /api/recipes?name=&page=&size=`, `GET /api/recipes/{id}` — one serving, with its figures.
- `GET /api/recipes/{id}/usage` — how many plates serve it and in whose diets (answered by the diet
  service, since the plates are its table).
- `POST /api/recipes` (201), `PUT /api/recipes/{id}` — `{name, steps?, rawText?, ingredients}`.
  Ingredients sent are kept with their matches; none sent reads `rawText`. A PUT is live for every
  plate that serves it.
- `DELETE /api/recipes/{id}` (204) — one no plate serves; otherwise a 400.

`POST /api/diets` takes `referenceProfileCode` and `clinical` too. Left out on a new diet, the
profile the patient's age suggests is used (the adult one when the age is unknown); a blank is
"none"; left out on `PUT`, the diet keeps the one it has. `parse` takes an optional `dietId` so that
diet's own measure criteria apply.

Creating, importing or copying a diet archives the one it replaces — that patient's, and only
theirs (`ARCHIVED`, `ended_on = today`) — in the same transaction, flushed before the insert so
their half of `uk_diets_active` is never held by two rows.

### Importing a diet

`helpers/DietWorkbookReader` (Apache POI) hands one sheet back as a rectangle of strings, the way
`food/helpers/DataReader` hands back the CSV, with merged regions spread across every cell they
cover. `DietImportService` owns no repository: it reads the header row for the days, the first
column for the meals, and passes each cell to `helpers/MealTextParser`, which splits
`Ensalada: lechuga (80 gr) + tomate (100 gr)` into a dish and its ingredients — outside brackets
only, so `(20 g: nueces + almendras)` stays one ingredient. A fragment with no readable quantity
still becomes an ingredient of one `unidad`; nothing is ever discarded. Writing without brackets
is read measure-first: `1 cdta AOVE` is `AOVE`, 1, `cdta`, and `1 kiwi` is `kiwi`, 1, `unidad`
(multi-word measures such as `cucharada sopera` are read whole). State words (`cocidas`, `en crudo`)
and size words (`mediano`) stay in the name and are also read into `state` and `size`. **When the
name and the bracket state opposite sides of cooking, the bracket wins** (FD-052,
`FoodState.ofWriting`): `Lenteja, cocida (55 g en seco)` is `DRY` and `pechuga a la plancha (150 g en
crudo)` is `RAW` — the bracket says in which state the grams were weighed, the name still says which
food it is. Where both agree, only one states anything, or the bracket contradicts itself
(`(60 g en crudo, 180 g cocidas)`, left null), the reading is unchanged. A row merged across the
day columns (`Comida`, `Cena`) names a meal whose dishes are the rows below it.

**A range stays a range** (`V11`): `2-3 nueces`, `(40-60 gr)`, `1 a 2 cdta` are read as `quantity` (the
lower end) and `quantity_max` (the upper). It used to be read as its last number, which silently chose
60 g. A ranged ingredient is weighed by nothing — `DietNutritionService` counts it `unmeasured`, the
ration count names it "Cantidad en intervalo, sin confirmar" — until a person settles it: a PATCH that
sends `quantity` clears `quantity_max`. `raw_text` keeps the range as written.

`service/FoodResolverService` matches the whole week against both catalogues in four batched calls
at most. The composition foods are asked first through `diet/helpers/ExactNames` (exactly, then
without size words — two `entitiesByName`, each answered from the in-memory crosswalk index and one
`findAllById`), and only the names they do not carry go to the branded one, through a `CachedLookup`
of names against ids. **The cache holds names against ids, never entities**: an entity cached across a
transaction is detached. The composition half keeps no cache of its own; its index is one, dropped on
every sync.

**Matching is exact and never guesses.** Case and accents are ignored, so `lechuga` finds `Lechuga`. A name that is not found is tried once more without
its size words (`kiwi mediano` → `Kiwi`): a size says how big the piece is, never which food it is. Anything less than exact is left unmatched and
offered as a *suggestion* instead — `ICompositionFoodService.suggest` ranks the crosswalk's Spanish
names in memory through `food/helpers/NameMatcher` and hands back the best few, and a person picks
one with a PATCH. Similarity alone puts `1 pan integral` on `Pan rallado` and `2 lonchas de jamón serrano`
on `Jamón asado`: close enough to score 100, wrong enough to put a false figure in someone's
diet. A blank is better than a wrong number, so the machine offers and the nutritionist decides.

Importing example-ui.xlsx matches 144 of its 210 ingredients outright (BEDCA matched 41); the other
66 come back from the fix-up endpoint each with its candidates.

### `alternative`

What else could go on the plate instead of this. It owns **no table and no schema**: it reaches
the composition foods through `ICompositionFoodService` and reads them through `INutritionService`,
the same way `diet` does, and adds a judgement about foods rather than a store of them. It is a
context of its own, and one endpoint, so that neither the catalogue nor the week has to grow a
second job.

**The judgement is in two halves and they are not interchangeable.** A *category* decides who is
eligible, and only then does *composition* decide the order. Grilled chicken is answered with
meats and fish, never with a lettuce, however well the figures line up — and they do line up: a
large enough portion of anything meets a small enough portion of anything else on paper, which is
exactly why the arithmetic is never allowed to make the first decision.

- `domain/FoodCategory` — the sixteen families, split the way a person cooking would split them
  rather than botanically. A tomato sits with the vegetables and a potato in `TUBER`, because
  neither is a plausible swap for the other.
- `helpers/FoodCategoriser` — the word list, and the first word of a name that any rule claims
  wins. BEDCA names are written head first (`Pollo, pechuga, plancha`), so the head is the food
  and the rest is preparation; reading left to right is what keeps `Aceite de hígado de bacalao`
  an oil, `Café, con leche` a drink and `Flan de huevo` a dessert. Two-word rules go first at
  each position for the few foods named after something they are not — `judía verde`,
  `nuez moscada`. It claims **956 of the 957** names; the one it does not is left uncategorised
  and offered nothing, and a word claimed by two families is a startup failure rather than a
  silent tie.
- `helpers/NutritionSimilarity` — the distance, and nothing else. Relative differences on five
  components (energy and protein weighted heaviest, then fat and carbohydrate, fibre breaking
  ties), each measured against a floor so a gram of fat against two reads as rounding. **Only
  components both foods publish are compared**, and fewer than two shared components is a null
  rather than a score resting on one number. Sugars are published for 205 of 957 foods and
  sodium says how a food was canned, so neither is counted.
- `service/AlternativeService` — one pass over the crosswalked foods per request
  (`ICompositionFoodService.entitiesNamed()`, one query): the category is read off every Spanish
  name, the wrong shelf is dropped before a figure is looked at, and the rest is ordered. Only foods
  the crosswalk names in Spanish have a family, so the shelf is the crosswalk (123 foods today; FD-036
  grows it) — a food without a Spanish name gets `category: null` and no alternatives. Rations are
  counted by the food's composition id and Spanish name. `AlternativeDto` carries `compositionFoodId`
  and `source`; `FoodAlternativesDto` carries `source`.

**Categories are derived on read and never stored**, like a kcal figure converted from kilojoules.
The source publishes a group for 182 of its 957 foods, so a stored category would exist for one
food in five; the name is the one thing every row has.

`AlternativeController` at `/api/alternatives`:

- `GET /api/alternatives/{foodId}?limit=&grams=&sameFood=&basis=&profile=` — alternatives to a
  composition-database food, best first.
- `GET /api/alternatives?name=&limit=&grams=&sameFood=&basis=&profile=` — the same for a food named the way a diet
  names it. **Exact only**, case- and accent-insensitively; anything less is a 404 rather than a
  guess, and is resolved by a person through the diet's fix-up list first.

`grams` asks what weight of each alternative carries the same **energy** as that portion — energy
because it is the one figure every row publishes — and a food whose energy is unpublished gets no
equivalent weight rather than an invented one. `basis` (`domain/EquivalenceBasis`) holds something else
equal instead: `CARBOHYDRATE`, `PROTEIN` or `FAT`, each with a floor per 100 g below which a food gets no
equivalent weight (the lettuce with a chicken breast's protein is a number no plate holds). The basis is
the *criterion* and never the eligibility — the category still decides who may stand in. `profile`
reads the portion and every equivalent weight in that reference profile's rations
(`RationEquivalentDto`, a range when the ration is), null when the profile counts the food in no group
or defines the ration in another state than the food. `sameFood` is false by default: six more cuts of
chicken are not an alternative to chicken, but `sameFood=true` is the right question for cheese,
where the same word is the whole family.

**The list travels with its counts**, as the diet's totals do. An empty `alternatives` has three
different meanings: `category` null (no rule recognised the name, so nothing was ever eligible),
`inCategory` zero (the shelf is empty), or `ranked` short of `inCategory` (foods were eligible but
published too few figures to compare).

### `reference`

**What a published guideline says a ration, a household measure or a week should be — each figure
with the document and page it was read from.** It owns the eight `ref_*` tables (`V8`, `V12`) and is loaded
from `reference-data/` (one folder per source, so a licence stays with its figures; see its
`README.md`) by `ReferenceStartupSync` at startup (`fdiet.reference.sync-on-startup`) and by
`POST /api/reference/sync`. Codes are stable keys, so a re-sync updates in place and a diet pointing
at a measure keeps pointing at it. A row names a food of `composition_foods` (CIQUAL 2025 / BLS 4.0,
FD-033 phase C) by `composition_source,composition_code` — the source's own key, since ids differ per
installation — resolved in one batched lookup; a row whose food is not loaded is skipped with a
reason, not failed.

**A food is asked about by its composition id and its Spanish name** (FD-033 phase D): the id reaches
the rows naming that food — the 5 al día rations and measures, every diet and global criterion — and
the name reaches family + keyword rows. A food without a Spanish name reaches only the rows naming it.
When no query of a batch names a composition food, the criteria are not read at all (every criterion
names one). A **picked** measure (`food_measure_id` with `measurePicked`, sent back on a `PUT`) is kept while its row is
still live for the same measure and still covers the food, even when the narrowing that ranks
candidates would leave it out (FD-039, `ReferenceMatcher.chooseMeasure`); otherwise the rule decides
again.

Only openly reusable sources are seeded: **AESAN 2022** (the default adult profile,
`fdiet.reference.default-adult-profile`), the **AESAN/MEC 2010** school consensus (four age bands, and
the only Spanish meal energy split, which the adult profile *borrows with a label*), **5 al día 2019**
per-fruit and per-vegetable portions (CC BY-SA 4.0), the **definitions** of the diabetes 10 g
carbohydrate ration and of the general 10 g exchanges (carbohydrate, protein, fat — Russolillo &
Marques-Lopes 2011, the unit only, never their food lists), and a selection of **USDA 2014 cooking
yields** for meat and poultry (public domain, `ref_yield_factors`, `V12`). SENC, DIAL, FINUT and the
Russolillo exchange lists need permission and are not loaded; `plan.md` has the research and the
licence classes.

**A cooking yield is only offered.** When an ingredient's written state disagrees with its matched food
(`150 g en crudo` against `Pollo, pechuga, plancha`), `DishIngredient.yieldHint` carries the nearest
published yield and what the quantity weighs in the food's state (≈ 108 g at 72 %). Rows reach foods by
family + keywords like rations; `method_keywords` rank a row whose method the cooked side names first,
and `methodNamed: false` says the source publishes no row for the method written. Nothing converts a
quantity by it.

- `domain/` — `FoodState` (read off head-first Spanish names, since LanguaL codes proved inconsistent),
  `PortionSize`, `HouseholdMeasure` (the kitchen words and every spelling of them, in code; an alias
  claimed twice is a startup failure), `WeightBasis`, `FoodKeywords`.
- `helpers/ReferenceMatcher` — pure. Which measure weighs an ingredient: the one a person picked;
  else the diet's own criterion if exactly one weighs; else the nutritionist's global criterion if
  exactly one weighs; else the only weighing row from the profile's source; else published rows that
  agree to the gram. **A range, a raw-state row for a cooked food and
  a size the text did not name never attach on their own** — they are offered as candidates. Which
  ration counts a food: a composition food id, or a `FoodCategory` narrowed by `;`-separated keywords
  (plurals allowed, longest phrase wins, `!` excludes). A row naming a food covers that composition
  food only, never a food known by its name alone.
- `service/ReferenceService` — owns every `ref_*` table except the nutritionist's rows of
  `ref_food_measures`; an in-memory snapshot of DTOs of the published rows, reset on sync. Its
  repository half is the package-private `ReferenceTables` (reads the snapshot, hands measure
  entities out, stores a sync's rows), split off by job, not by table; nothing else calls it.
  `ReferenceImportService` owns no repository and reads the CSVs by header.
- `service/MeasureCriterionService` (`IMeasureCriterionService`) — owns the nutritionist's rows of
  `ref_food_measures`, split from the published ones by who writes them: one diet's criteria and her
  global ones. `ReferenceService` reads both from it per request (one query each) when it chooses a
  measure, and delegates the diet-criterion methods to it.
- `diet/service/MeasureResolverService` (`IMeasureResolverService`) — the diet's side of choosing a
  measure, the counterpart of `FoodResolverService`: which ingredients need one, asked in one batch,
  handed back as entities a recipe can point at. Owns no table.
- `diet/service/DietRationService` — the week in rations, derived on read and stored nowhere. A
  range ration divides into a range count; a gross ration is cut to its edible part; cooked lentils
  against a dry ration are not counted but named with the reason. Every day carries `coverage`
  (`counted + unmatched + unweighed + noRation + stateMismatch = ingredients`). Recommendations come
  back `WITHIN`/`BELOW`/`ABOVE`/`UNCERTAIN`; while anything that could belong to a group was left
  uncounted, a `BELOW` (and a `WITHIN` against a ceiling) is `UNCERTAIN`, because the count is only a
  floor. The clinical carbohydrate ration is counted only on a diet marked `clinical`; the general
  exchanges on every diet, per day, per meal and per dish (`DishUnits`, addressed by slot like a score).

`ReferenceController` at `/api/reference`: `GET sources`, `GET profiles?ageMonths=` (the suggested one
is marked, never applied), `GET profiles/{code}`, `GET rations?profile=&compositionFoodId=`,
`GET measures?compositionFoodId=&unit=&dietId=&profile=` (the diet's criteria, then the global ones,
then published rows), `GET vocabulary`,
`GET exchange-systems?clinical=`, `GET yields?compositionFoodId=`, `POST sync` (answers with every
source's attribution). `measures` and `yields` require `compositionFoodId`; `rations` without one lists
the profile's rations. `bedcaFoodId` on any of the three is a 400 (FD-033 phase D). A composition
food without a Spanish name reaches only the rows naming it (no family). `RationDto` and
`FoodMeasureDto` carry `compositionFoodId`.

**The machine offers, the nutritionist decides**, here as in food matching: a nutritionist may give a
measure their own weight for one diet (`ref_food_measures.diet_id`, deleted with the diet and copied
with it), and that criterion is labelled as theirs, never as published data.

**Her global criterion** (`V14`, `ref_food_measures.global_criterion`) is the same idea for every diet
of every patient: one composition food (CIQUAL / BLS), one household measure (+ optional size), a point weight per unit
(`huevo mediano = 58 g` where AESAN publishes only `53–63 g`; `rebanada de pan de molde = 30 g` where no
source publishes anything). It belongs to no diet and no source, so it weighs library recipes, an unsaved
week (`compose`/`parse` without `dietId`) and logged extras alike; `FoodMeasureDto.globalOwn` labels it.
**Precedence: picked > diet criterion > global criterion > published.** The weight is the edible part
(`NET_EDIBLE`), as a diet's criterion is.

- **The sync never touches it.** The sync addresses rows by `code`, and `ck_ref_food_measures_global`
  keeps a global criterion without code or source; the published snapshot is read by
  `findByDietIdIsNullAndGlobalCriterionFalse…`, so a criterion is never mistaken for published data.
- **One per food, measure and size**: `criterion_key` is a generated column (the `library_key` trick)
  under `uk_ref_food_measures_criterion`; the service checks first for a message, the index is underneath.
- **A change is live** for every ingredient and extra pointing at it, like a library recipe's. `GET …/usage`
  says how many before a save. While in use, its food, measure and size are fixed (moving it would weigh
  those rows as another food) — only the weight and note change.
- **Delete is refused while in use** (400). The ingredient and extra foreign keys are `ON DELETE SET NULL`
  (right for a diet's criterion, which goes with its diet), so the check is a count, answered by the
  owning services through the `IMeasureUsageCounter` port (`RecipeService`, `JournalService`): the
  reference module may not call into `diet` or `journal`, so it declares the question and they answer
  it. Looked up lazily (`ObjectProvider`) because those services depend on `IReferenceService`.
- **Writing a criterion re-weighs what it reaches** (FD-054). After a global criterion is created or
  changed, or a diet's criterion is PUT, `MeasureCriterionService` asks the `IMeasureReweigher` port —
  `diet/service/RecipeMeasureReweigher` (owns no table: reads and writes through `IRecipeService`, asks
  `IDietService.dietsServingPrivate` which diet each private recipe is served in) and `JournalService` —
  in the same transaction. Each re-chooses, by the publish rule, the measure of every row matched to the
  criterion's food and written in its measure whose measure nobody picked: in every diet, archived
  included, plus library recipes for a global criterion; only that diet's private recipes and extras for
  a diet's criterion. A private recipe is weighed inside its diet (its criteria and profile), a library
  recipe inside none. The rows of many diets go through `IReferenceService.rechoose` in one batch: five
  queries for the ingredients and four for the extras, whatever the number of rows or diets, then one
  batched write each (`reference/helpers/Remeasure` applies the answer and counts the rows that changed).
  A row whose new choice is "none" (the criterion made the choice a judgement) is counted and left
  unweighed, as a publish would leave it.

`MeasureCriterionController` at `/api/reference/criteria` — not paged: one person's criteria, and every
weighing reads them anyway:

- `GET /api/reference/criteria?compositionFoodId=` — every global criterion by food, or one food's.
- `GET /api/reference/criteria/{id}`, `GET /api/reference/criteria/{id}/usage` —
  `{measureId, ingredients, extraFoods}`.
- `POST /api/reference/criteria` (201), `PUT /api/reference/criteria/{id}` —
  `{measure, size?, compositionFoodId, grams | ml, note?}` (the same body as a diet's criterion). A second
  criterion for the same food, measure and size is a 400. Answers `{measure, reweighed: {ingredients,
  extraFoods}}`: what the save re-weighed in every diet and library recipe (FD-054).
- `DELETE /api/reference/criteria/{id}` (204) — one nothing is weighed by; otherwise a 400.

### `journal`

**The patient's side of the plan.** A diet says what to eat; the journal says what was thought of
it and what was eaten instead. It owns two tables and adds nothing to the week — the same reason
`alternative` is a context of its own rather than a second job for the catalogue.

- `model/DishScore` → `dish_scores` — what the patient thought of one plate, 1–5.
- `model/ExtraFood` → `extra_foods` — something eaten that the plan did not prescribe.
- `service/JournalService` — owns both tables. It reaches the week through `IDietService` and the
  catalogue through `ICompositionFoodService` / `IFoodItemService`, never through their repositories.
- `service/JournalNutritionService` — the mirror of `DietNutritionService` over this context's own
  rows. It **borrows `IPortionScaler`** rather than keeping a unit table of its own: there is one
  answer to what a millilitre weighs, and two copies of it would drift until a day's plan and that
  day's extras disagreed about the same word.

**A score points at a slot, not at a dish row, and this is the whole design.** `PUT /api/diets/{id}`
replaces a diet's *whole week*, so every `diet_dishes` id is new after any publish. A score holding
one of those ids would be taken by the cascade every time the nutritionist edited a single cell —
the patient's entire record wiped by a change to one breakfast. So `uk_dish_scores_slot` keys on
`(diet_id, day_of_week, meal_type, dish_index)`, which is what `uk_diet_meals_slot` already treats
as the identity of a place in the week, and `update()` keeps the same `diets` row so the diet
foreign key survives too. The cost is stated rather than hidden: if a republish puts a *different*
dish in the slot, the score stays and now describes that one. Losing every score on every publish
is worse, and `scored_at` is there so the two can be told apart.

`ExtraFood` is shaped like `RecipeIngredient` because it is the same idea from the other side:
`raw_name` always kept, a quantity with its own unit, and **at most one** of `composition_food_id` /
`food_item_id` set (`POST …/extras` takes `compositionFoodId`; `bedcaFoodId` is a 400;
`ExtraFoodDto` carries `compositionFoodId` and `matchedSource`). Both null is an entry nothing matched — kept on the record, counted towards
nothing. The branded half is the usual match here, the reverse of the week: a diet says `lechuga`,
while a patient logging an extra is normally holding a wrapper with an EAN on it.

**An extra is weighed by the same household measures as the week** (`V11`: `extra_foods.state`,
`portion_size`, `food_measure_id`). `POST …/extras` takes an optional `foodMeasureId`; without one,
`JournalService` asks `IReferenceService.chooseMeasures` with the diet's profile and own criteria, so a
measure attaches on its own only when the choice is not a judgement. A `foodMeasureId` sent is stored
as the person's pick (`measurePicked`); one the rule attached follows a criterion written later
(FD-054). Both nutrition services weigh
through `IPortionScaler.weigh(quantity, unit, measure, ediblePortion)`, one door for both.

There is **no score of zero**. Having no opinion has to stay out of the average rather than drag it
down, so taking a rating back is a DELETE of the slot's score. `averageScore` is null while nothing
has been scored, and travels with `scored` — the count it was worked out over — the way every other
total in this codebase travels with its counts.

`JournalController` at `/api/journal`:

- `GET /api/journal/{dietId}` — a whole week's scores and off-plan entries in one answer, since the
  screen that reads it draws all seven days at once.
- `GET /api/journal/{dietId}/counts` — `{dietId, scored, extras}`: how many plates were scored and
  how many extras logged, one `count` query each (404 for an unknown diet). What deleting the diet
  would take with it, for the confirm; answered here because the two tables are this service's.
- `PUT /api/journal/{dietId}/scores/{day}/{mealType}/{dishIndex}` — score one plate, writing over
  any earlier opinion of the same slot. 400 when the diet has no dish there, checked with one count
  query through `IDietService.hasDishAt`.
- `DELETE /api/journal/{dietId}/scores/{day}/{mealType}/{dishIndex}` — take a score back.
- `POST /api/journal/{dietId}/extras` (201) / `DELETE /api/journal/{dietId}/extras/{extraId}` (204).

## Persistence

MySQL via `com.mysql:mysql-connector-j`. **Flyway owns the schema** — migrations live in
`src/main/resources/db/migration` and run at startup. `spring.jpa.hibernate.ddl-auto=validate`,
so Hibernate checks the entities against the migrated schema and never emits DDL itself: after
changing an entity, add a migration to match or startup fails.

| Migration | What it does |
| --- | --- |
| `V1__create_food_schema.sql` | the branded catalogue |
| `V2__create_diet_schema.sql` | the diet and its week |
| `V3__diet_ingredient_raw_name.sql` | `food_item_id` becomes nullable, `raw_name` appears |
| `V4__create_bedca_schema.sql` | `bedca_foods`, and `diet_ingredients.bedca_food_id` |
| `V5__diet_dish_raw_text.sql` | `diet_dishes.raw_text` — the cell as the nutritionist wrote it |
| `V6__create_journal_schema.sql` | `dish_scores` and `extra_foods` — the two tables the patient writes |
| `V7__create_patient_schema.sql` | `patients`, seeded with `Victor`; `diets.patient_id`; `uk_diets_active` becomes per patient |
| `V8__create_reference_schema.sql` | the seven `ref_*` tables: sources, populations, rations, food measures, recommendations, meal shares, exchange systems |
| `V9__patient_profile_and_diet_reference.sql` | `patients.birth_date` / `sex`; `diets.reference_profile_code` / `clinical` |
| `V10__ingredient_measure_state.sql` | `diet_ingredients.state`, `portion_size`, `food_measure_id` |
| `V11__ingredient_range_and_extra_measure.sql` | `diet_ingredients.quantity_max`; `extra_foods.state`, `portion_size`, `food_measure_id` |
| `V12__create_yield_factors.sql` | `ref_yield_factors` — published cooking yields, offered and never applied |
| `V13__create_recipes.sql` | `recipes`; `diet_dishes.recipe_id`, `servings`; `diet_ingredients` becomes `recipe_ingredients`; `raw_text` moves to the recipe |
| `V14__global_measure_criteria.sql` | `ref_food_measures.global_criterion` + generated `criterion_key` (unique): the nutritionist's measure criteria for every diet |
| `V15__create_composition_foods.sql` | `composition_foods`: CIQUAL 2025 + BLS 4.0 as published (`uk (source, source_code)`), plus fdiet's crosswalk columns (Spanish name, aliases, preferred, reviewed, SR Legacy edible portion) |
| `V16__rekey_reference_foods_to_composition.sql` | `ref_rations` / `ref_food_measures`: `bedca_food_id` replaced by `composition_food_id` (FK, indexed), re-keyed through the approved BEDCA→CIQUAL/BLS mapping seeded in the migration; `criterion_key`, `ck_ref_food_measures_global` and `idx_ref_food_measures_global` rebuilt on it. A diet or global criterion without an equivalent stops the migration by name (`SIGNAL`), before anything changes; a published row without one is made inert until the next sync |
| `V17__point_ingredients_and_extras_at_composition_foods.sql` | `recipe_ingredients` / `extra_foods`: `composition_food_id` (FK, indexed) and a check that at most one of it and `food_item_id` is set; every `bedca_food_id` reset to NULL (columns kept until phase E) |
| `V18__rematch_ingredients_by_composition_name` (Java, `src/main/java/db/migration/`) | re-matches every ingredient and extra without a food by exact Spanish name / alias of the crosswalk (size-word retry), the import's rule; releases a measure whose row names another food |
| `V19__measure_picked_flag.sql` | `recipe_ingredients.measure_picked` / `extra_foods.measure_picked` (FD-054): whether a person picked the measure; every stored measure starts as picked; `idx_*_food_picked (composition_food_id, measure_picked)` |
| `V20__free_measures_the_rule_chooses` (Java) | marks as the rule's (`measure_picked = FALSE`) every stored measure the publish rule would choose today anyway — over the published rows, the row's diet's criteria (none for a library recipe), the global criteria and the diet's profile source; anything else stays picked, since nothing recorded who chose it. Applied to the dev DB 2026-10-04: 0 rows (no ingredient or extra had a measure) |

## Data files and licensing

Two datasets sit in the repository root and both are tracked:

- `fooddata.csv` — the branded catalogue, read by `POST /api/food/sync`.
- `bedca_foods.csv` — the composition database, read by `POST /api/bedca/sync`. Copied from
  <https://github.com/TerjeRu/bedca-database>, itself a retrieval of <https://www.bedca.net/>.

**bedca_foods.csv is not unencumbered.** Attribution is required wherever the figures are shown,
application credits included; the values may not be modified or normalised; and use is limited to
personal, educational or **non-commercial** purposes without AESAN's express authorisation. The
full terms and the attribution string are in `BEDCA-ATTRIBUTION.txt`, and `POST /api/bedca/sync`
returns the attribution in its response so no caller can store the data without being handed it.

`reference-data/composition/` holds the open composition tables, each **unmodified upstream file**
in its own folder with a `LICENSE.md` carrying its attribution, plus `manifest.csv` (per file: URL or
landing page, DOI, retrieval date, bytes, SHA-256, SPDX id). Its `.gitattributes` switches off
line-ending conversion so the hashes stay true, and routes the `.xlsx` and `.pdf` snapshots
(about 17 MB) through **Git LFS**: a clone needs `git lfs install` first, or those files are pointer
files and the composition sync fails. `ciqual-2025/` (Zenodo, 10.5281/zenodo.17550133) and
`bls-4.0/` (DOI 10.25826/Data20251217-134202-0; the download link carries a rotating token, so the
page and the DOI are recorded, not the link) are **CC BY 4.0**; `usda-sr-legacy/refuse.csv` is an
extract of SR Legacy's refuse figures, **CC0**; `composition-es/` is fdiet's own work, **CC BY 4.0**:
the Spanish-name crosswalk `links.csv` and the list of CIQUAL foods without energy. fdiet's joins
never go inside an upstream file. `POST /api/composition/sync` returns the CIQUAL and BLS
attribution strings.

`reference-data/` holds the reference CSVs, each folder under its own source's terms; the 5 al día
figures are **CC BY-SA 4.0**, so a derived file stays ShareAlike; the USDA yields are US public domain.
Both the builder and the patient screen show the CIQUAL 2025 and BLS 4.0 attribution lines (FD-041;
`SOURCE_ATTRIBUTIONS` in `UI/src/domain/compositionFood.ts`, copied word for word from
`CompositionSource`, since no endpoint hands them over without a sync) and every reference source a
count used in their footer. No BEDCA figure is shown on any screen since FD-033 phase D, so its line
is gone.

## Dependencies

Declared in `build.gradle`: Spring Web, Spring Data JPA, Bean Validation, MySQL connector,
Flyway (`flyway-core` + `flyway-mysql`), springdoc-openapi (Swagger UI at `/swagger-ui.html`),
and Lombok (`@Getter`/`@Setter` on the entities only).
