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
`SERVER_PORT`, `FOOD_CSV_PATH` and `BEDCA_CSV_PATH`.

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

`food_items` and `bedca_foods` are both foods and they answer different questions.

| | `food_items` | `bedca_foods` |
| --- | --- | --- |
| what | branded commercial products | generic Spanish foods |
| source | fooddata.csv, keyed on EAN | bedca_foods.csv, AESAN/BEDCA v1.0 |
| size | ~100 k rows | 957 rows |
| a name | `BEKIND BARRA CEREAL CARAMELO ALMENDRA Y SAL` | `Lechuga`, `Pollo, pechuga, plancha` |

A diet says `lechuga (80 gr)`, so **the composition database is the half that answers**: an
import of example-ui.xlsx matched 0 of 210 ingredients against `food_items` and matches a
useful share against `bedca_foods`. The branded catalogue is still there for the days a diet
names a product outright.

### `bedca_foods` — the composition database

- `controller/BedcaController` — `@RestController` at `/api/bedca`:
  - `GET /api/bedca?name=&page=&size=` — page of foods.
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
adding one is a constant in `Nutrient`, a field on `BedcaFood` and a column pair in a migration.

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
the foreign key itself rather than a query, because `diets` is another context's table.

`V7` seeds one patient, `Victor`, and hands every diet that already existed to them — they were all
written for one person. The row is seeded on an empty database too: a screen that picks a patient
before it can do anything needs one to exist before the first diet does.

### `diet`

Every diet belongs to one patient, and there is **one active diet per patient** plus a record of
their archived ones; nothing generates a week, the nutritionist writes it. The module keeps two
shapes of the same idea apart:

- `model/` — the JPA entities `DietPlan`, `PlannedMeal`, `PlannedDish`, `PlannedIngredient` →
  `diets`, `diet_meals`, `diet_dishes`, `diet_ingredients`. Named `Planned*` so the bare `Diet`
  and `Meal` stay with the in-memory classes and no file ever imports two of either.
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

`PlannedIngredient` carries `raw_name` — what the diet calls the food — and *may* point at one of
the two catalogues: `bedca_food_id` for a generic composition-database food, the usual match, or
`food_item_id` for a branded product. **Both are nullable, on purpose, and only one is ever set.**
Both null means "not matched yet": the ingredient is stored exactly as written and matched later
through `PATCH /api/diets/{id}/ingredients/{ingredientId}`, because dropping it would silently
lose part of the week. Nutrition figures for an ingredient are only available once it is matched.
The entity associations are mappings, not layer crossings — when the diet service needs food
*data* it goes through `IBedcaFoodService` / `IFoodItemService`, never a food repository.

`PlannedDish` carries `raw_text` for the same reason one level up: **the cell as it was written**.
Reading a sentence into a name and quantities cannot be undone — `MealTextParser` keeps one
quantity per ingredient and no brackets in a name, so `Tostada (60 gr) con tomate (80 gr)` puts
back together as `Tostada con tomate (80 gr) (60 gr)`, the same food and a moved weight. Since
`PUT /api/diets/{id}` replaces the whole week, an editor changing one cell has to send the other
sixty-nine back; without the sentence it could only send rebuilt ones, and a nutritionist would be
editing a rewrite of what they typed. **It is nullable and never reconstructed**: null means "what
was written is not known", which is the truth for every dish stored before `V5`, and an invented
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

**A household measure weighs only through a row that says so.** `diet_ingredients.food_measure_id`
(`V10`) points at a `ref_food_measures` row — published (`1 cucharada sopera` of olive oil, 10 ml,
AESAN 2022) or the diet's own criterion — and `PortionScaler.weigh` uses it: the row's point weight
divided by its count (`3 Uds. medianas = 180 g` is 60 g each), cut to the edible part by the food's
`edible_portion` when the row is a gross weight (and refused when that fraction is unpublished). A
range (`53–63 g`) weighs nothing, and a measure weighs only the unit it measures. `countedByMeasure`
says how much of `counted` rests on one.

### Interfaces and injection

Every class in `diet/`, `alternative/` and `patient/` is injected through an interface
(`IDietService`, `IDietMapper`, `IMealTextParser`, `IPortionScaler`, `IDietNutritionService`,
`IAlternativeService`, `IFoodCategoriser`, `INutritionSimilarity`, `IPatientService`,
`IPatientMapper`, `IReferenceService`, `IReferenceMapper`, `IDietRationService`, …), as are the food services
they depend on: `IFoodItemService`, `IBedcaFoodService`, `INutritionService`, `INameMatcher`,
`IBedcaImportService`, `IBedcaFoodMapper`. The older `food/` classes
(`FoodItemService`'s siblings, `FoodImportService`) still use their concrete types. The
repositories are Spring Data interfaces already.

### Endpoints

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
  the diet already belongs to.
- `POST /api/diets/{id}/copy` (201) — `{patientId, name?, startedOn?}`. Writes the same week again
  for another patient: its days, dishes, `raw_text` and every food match already made. It becomes
  their diet in force, archiving what they were on; the source is untouched and the **journal is
  not copied**.
- `GET /api/diets/active?patientId=`, `GET /api/diets/{id}` — the week, ordered by day and slot.
- `GET /api/diets/current` — every patient's diet in force, without their weeks: who is on a diet
  right now, in one query. This is the board the UI's patient selector is drawn from.
- `GET /api/diets?patientId=&page=&size=` — that patient's archived diets, without their weeks.
- `GET /api/diets/{id}/ingredients?resolved=false&suggest=true&page=&size=` — the fix-up list.
  `suggest=true` attaches the composition database's best candidates to each unmatched
  ingredient, ranked, costing no query.
- `PATCH /api/diets/{id}/ingredients/{ingredientId}` — match one ingredient to a food
  (`bedcaFoodId` or `foodItemId`, not both — matching to one releases the other), or correct its
  name, quantity or unit. Fields left out are left alone.
- `POST /api/diets/parse` — reads one written cell (`text`, optional `slotName`) into a dish with
  its ingredients matched and priced, **storing nothing**. It exists so the editor never has a
  parser of its own: a second implementation would drift from the importer, and the two would then
  disagree about what the same line of text means.
- `POST /api/diets/import` — multipart `file`, required `patientId`, optional `sheet`, `name`,
  `startedOn`, `referenceProfile`, `clinical`.
- `PATCH /api/diets/{id}` — `{name?, referenceProfileCode?, clinical?}` without sending the week.
  A blank `referenceProfileCode` takes the profile off.
- `POST /api/diets/compose` — `{bedcaFoodId, grams | foodMeasureId + count, state?, dietId?}` →
  the text fragment to append to a cell (`Lenteja, seca, cruda (60 g en crudo)`) and what the parser
  reads back from it. The editor's "añadir por raciones" goes through this, so it still has no parser.
- `GET /api/diets/{id}/rations?profile=` — the week counted in rations (see `reference`).
- `GET /api/diets/{id}/measures`, `PUT /api/diets/{id}/measures`, `DELETE /api/diets/{id}/measures/{measureId}`
  — the diet's own measure criteria (`{measure, size?, bedcaFoodId, grams | ml, note?}`). A PUT
  attaches the criterion wherever it is now the chosen measure and answers how many.

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
and size words (`mediano`) stay in the name and are also read into `state` and `size`. A row merged across the
day columns (`Comida`, `Cena`) names a meal whose dishes are the rows below it.

**A range stays a range** (`V11`): `2-3 nueces`, `(40-60 gr)`, `1 a 2 cdta` are read as `quantity` (the
lower end) and `quantity_max` (the upper). It used to be read as its last number, which silently chose
60 g. A ranged ingredient is weighed by nothing — `DietNutritionService` counts it `unmeasured`, the
ration count names it "Cantidad en intervalo, sin confirmar" — until a person settles it: a PATCH that
sends `quantity` clears `quantity_max`. `raw_text` keeps the range as written.

`service/FoodResolverService` matches the whole week against both catalogues in four batched calls
at most: `entitiesByName` for the names it has not seen, `entitiesByIds` for the ones its cache
already knows. The composition database is asked first, and only the names it does not carry go
to the branded one. Both halves share one `CachedLookup`, so neither has its own copy of the
logic. **The caches hold names against ids, never entities**: an entity cached across a
transaction is detached.

**Matching is exact and never guesses.** The `utf8mb4_unicode_ci` collation is case- and
accent-insensitive, so `lechuga` finds `Lechuga`. A name that is not found is tried once more without
its size words (`kiwi mediano` → `Kiwi`): a size says how big the piece is, never which food it is. Anything less than exact is left unmatched and
offered as a *suggestion* instead — `IBedcaFoodService.suggest` ranks all 957 names in memory
through `food/helpers/NameMatcher` and hands back the best few, and a person picks one with a
PATCH. Similarity alone puts `1 pan integral` on `Pan rallado` and `2 lonchas de jamón serrano`
on `Jamón asado`: close enough to score 100, wrong enough to put a false figure in someone's
diet. A blank is better than a wrong number, so the machine offers and the nutritionist decides.

Importing example-ui.xlsx matches 41 of its 210 ingredients outright; the other 169 come back
from the fix-up endpoint each with its candidates.

### `alternative`

What else could go on the plate instead of this. It owns **no table and no schema**: it reaches
the composition database through `IBedcaFoodService` and reads it through `INutritionService`,
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
- `service/AlternativeService` — one pass over the catalogue per request: the category is read
  off every name, the wrong shelf is dropped before a figure is looked at, and the rest is
  ordered. 957 rows is one query and no index of its own to fall stale after a sync, which is
  why `IBedcaFoodService.entitiesAll()` exists.

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
at a measure keeps pointing at it. A row naming a BEDCA food that is not loaded is skipped with a
reason, not failed.

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

- `domain/` — `FoodState` (read off BEDCA names, since LanguaL codes proved inconsistent),
  `PortionSize`, `HouseholdMeasure` (the kitchen words and every spelling of them, in code; an alias
  claimed twice is a startup failure), `WeightBasis`, `FoodKeywords`.
- `helpers/ReferenceMatcher` — pure. Which measure weighs an ingredient: the one a person picked;
  else the diet's own criterion if exactly one weighs; else the only weighing row from the profile's
  source; else published rows that agree to the gram. **A range, a raw-state row for a cooked food and
  a size the text did not name never attach on their own** — they are offered as candidates. Which
  ration counts a food: a BEDCA id, or a `FoodCategory` narrowed by `;`-separated keywords (plurals
  allowed, longest phrase wins, `!` excludes).
- `service/ReferenceService` — owns every `ref_*` table; an in-memory snapshot of DTOs, reset on
  sync. `ReferenceImportService` owns no repository and reads the CSVs by header.
- `diet/service/DietRationService` — the week in rations, derived on read and stored nowhere. A
  range ration divides into a range count; a gross ration is cut to its edible part; cooked lentils
  against a dry ration are not counted but named with the reason. Every day carries `coverage`
  (`counted + unmatched + unweighed + noRation + stateMismatch = ingredients`). Recommendations come
  back `WITHIN`/`BELOW`/`ABOVE`/`UNCERTAIN`; while anything that could belong to a group was left
  uncounted, a `BELOW` (and a `WITHIN` against a ceiling) is `UNCERTAIN`, because the count is only a
  floor. The clinical carbohydrate ration is counted only on a diet marked `clinical`; the general
  exchanges on every diet, per day, per meal and per dish (`DishUnits`, addressed by slot like a score).

`ReferenceController` at `/api/reference`: `GET sources`, `GET profiles?ageMonths=` (the suggested one
is marked, never applied), `GET profiles/{code}`, `GET rations?profile=&bedcaFoodId=`,
`GET measures?bedcaFoodId=&unit=&dietId=&profile=`, `GET vocabulary`,
`GET exchange-systems?clinical=`, `GET yields?bedcaFoodId=`, `POST sync` (answers with every source's
attribution).

**The machine offers, the nutritionist decides**, here as in food matching: a nutritionist may give a
measure their own weight for one diet (`ref_food_measures.diet_id`, deleted with the diet and copied
with it), and that criterion is labelled as theirs, never as published data.

### `journal`

**The patient's side of the plan.** A diet says what to eat; the journal says what was thought of
it and what was eaten instead. It owns two tables and adds nothing to the week — the same reason
`alternative` is a context of its own rather than a second job for the catalogue.

- `model/DishScore` → `dish_scores` — what the patient thought of one plate, 1–5.
- `model/ExtraFood` → `extra_foods` — something eaten that the plan did not prescribe.
- `service/JournalService` — owns both tables. It reaches the week through `IDietService` and the
  catalogue through `IBedcaFoodService` / `IFoodItemService`, never through their repositories.
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

`ExtraFood` is shaped like `PlannedIngredient` because it is the same idea from the other side:
`raw_name` always kept, a quantity with its own unit, and **at most one** of `bedca_food_id` /
`food_item_id` set. Both null is an entry nothing matched — kept on the record, counted towards
nothing. The branded half is the usual match here, the reverse of the week: a diet says `lechuga`,
while a patient logging an extra is normally holding a wrapper with an EAN on it.

**An extra is weighed by the same household measures as the week** (`V11`: `extra_foods.state`,
`portion_size`, `food_measure_id`). `POST …/extras` takes an optional `foodMeasureId`; without one,
`JournalService` asks `IReferenceService.chooseMeasures` with the diet's profile and own criteria, so a
measure attaches on its own only when the choice is not a judgement. Both nutrition services weigh
through `IPortionScaler.weigh(quantity, unit, measure, ediblePortion)`, one door for both.

There is **no score of zero**. Having no opinion has to stay out of the average rather than drag it
down, so taking a rating back is a DELETE of the slot's score. `averageScore` is null while nothing
has been scored, and travels with `scored` — the count it was worked out over — the way every other
total in this codebase travels with its counts.

`JournalController` at `/api/journal`:

- `GET /api/journal/{dietId}` — a whole week's scores and off-plan entries in one answer, since the
  screen that reads it draws all seven days at once.
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

`reference-data/` holds the reference CSVs, each folder under its own source's terms; the 5 al día
figures are **CC BY-SA 4.0**, so a derived file stays ShareAlike; the USDA yields are US public domain.
Both the builder and the patient screen show BEDCA's line and every reference source a count used in
their footer.

## Dependencies

Declared in `build.gradle`: Spring Web, Spring Data JPA, Bean Validation, MySQL connector,
Flyway (`flyway-core` + `flyway-mysql`), springdoc-openapi (Swagger UI at `/swagger-ui.html`),
and Lombok (`@Getter`/`@Setter` on the entities only).
