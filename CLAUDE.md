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
The feature modules are `food` (the product catalogue) and `diet` (the plan built on it);
`common` holds the shared transport types.

**No security layer.** No Spring Security, no JWT, no authentication anywhere — every route is
open. This is intentional for the current stage; the previous `users` bounded context was removed.

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

### `diet`

There is **one active diet at a time** plus a record of the archived ones; nothing generates a
week, the nutritionist writes it. The module keeps two shapes of the same idea apart:

- `model/` — the JPA entities `DietPlan`, `PlannedMeal`, `PlannedDish`, `PlannedIngredient` →
  `diets`, `diet_meals`, `diet_dishes`, `diet_ingredients`. Named `Planned*` so the bare `Diet`
  and `Meal` stay with the in-memory classes and no file ever imports two of either.
- `domain/` — `Diet` and `Meal`, plain in-memory classes that validate a submitted week (a day
  once per diet, a meal once per slot) and hand it back ordered by `DayOfWeek` / `MealType`
  through their `EnumMap`s. Entities read from the database get their ordering from here rather
  than from an `@OrderBy` on the days.
- `repository/DietRepository` — `findFirstByStatus(ACTIVE)` is the main question: the diet in
  force now. `findByStatusNotOrderByStartedOnDesc` is the history.

Two invariants live in the schema, not only in the code:

- `diets.active_flag` is a generated column (1 while `status = 'ACTIVE'`, NULL once archived)
  under the unique index `uk_diets_active`. MySQL allows many NULLs in a unique index, so any
  number of archived diets fit and a second active one cannot be inserted. **The entity does not
  map that column** — Hibernate validates only mapped columns, so an insert can never write it.
- `uk_diet_meals_slot (diet_id, day_of_week, meal_type)` restates `domain/Meal`'s one-meal-per-slot
  rule at the database level.

`PlannedIngredient` carries `raw_name` — what the diet calls the food — and *may* point at one of
the two catalogues: `bedca_food_id` for a generic composition-database food, the usual match, or
`food_item_id` for a branded product. **Both are nullable, on purpose, and only one is ever set.**
Both null means "not matched yet": the ingredient is stored exactly as written and matched later
through `PATCH /api/diets/{id}/ingredients/{ingredientId}`, because dropping it would silently
lose part of the week. Nutrition figures for an ingredient are only available once it is matched.
The entity associations are mappings, not layer crossings — when the diet service needs food
*data* it goes through `IBedcaFoodService` / `IFoodItemService`, never a food repository.

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
written in a unit nothing can weigh (`1 unidad`, `1 cdta`) — `PortionScaler` refuses to guess
what a spoonful weighs. Its one assumption is that a millilitre is a gram, which a diet's
liquids (water, broth, milk, juice) are within a few percent of.

### Interfaces and injection

Every class in `diet/` is injected through an interface (`IDietService`, `IDietMapper`,
`IMealTextParser`, `IPortionScaler`, `IDietNutritionService`, …), as are the food services the
diet module depends on: `IFoodItemService`, `IBedcaFoodService`, `INutritionService`,
`IBedcaImportService`, `IBedcaFoodMapper`, `INameMatcher`. The older `food/` classes
(`FoodItemService`'s siblings, `FoodImportService`) still use their concrete types. The
repositories are Spring Data interfaces already.

### Endpoints

`DietController` at `/api/diets` — plain REST, no HATEOAS envelope, pagination as query
parameters:

- `POST /api/diets` — store a week as the diet in force (201).
- `PUT /api/diets/{id}` — replace a diet's whole week.
- `GET /api/diets/active`, `GET /api/diets/{id}` — the week, ordered by day and slot.
- `GET /api/diets?page=&size=` — the archived diets, without their weeks.
- `GET /api/diets/{id}/ingredients?resolved=false&suggest=true&page=&size=` — the fix-up list.
  `suggest=true` attaches the composition database's best candidates to each unmatched
  ingredient, ranked, costing no query.
- `PATCH /api/diets/{id}/ingredients/{ingredientId}` — match one ingredient to a food
  (`bedcaFoodId` or `foodItemId`, not both — matching to one releases the other), or correct its
  name, quantity or unit. Fields left out are left alone.
- `POST /api/diets/import` — multipart `file`, optional `sheet`, `name`, `startedOn`.

Creating or importing a diet archives the one it replaces (`ARCHIVED`, `ended_on = today`) in the
same transaction, flushed before the insert so `uk_diets_active` is never held by two rows.

### Importing a diet

`helpers/DietWorkbookReader` (Apache POI) hands one sheet back as a rectangle of strings, the way
`food/helpers/DataReader` hands back the CSV, with merged regions spread across every cell they
cover. `DietImportService` owns no repository: it reads the header row for the days, the first
column for the meals, and passes each cell to `helpers/MealTextParser`, which splits
`Ensalada: lechuga (80 gr) + tomate (100 gr)` into a dish and its ingredients — outside brackets
only, so `(20 g: nueces + almendras)` stays one ingredient. A fragment with no readable quantity
still becomes an ingredient of one `unidad`; nothing is ever discarded. A row merged across the
day columns (`Comida`, `Cena`) names a meal whose dishes are the rows below it.

`service/FoodResolverService` matches the whole week against both catalogues in four batched calls
at most: `entitiesByName` for the names it has not seen, `entitiesByIds` for the ones its cache
already knows. The composition database is asked first, and only the names it does not carry go
to the branded one. Both halves share one `CachedLookup`, so neither has its own copy of the
logic. **The caches hold names against ids, never entities**: an entity cached across a
transaction is detached.

**Matching is exact and never guesses.** The `utf8mb4_unicode_ci` collation is case- and
accent-insensitive, so `lechuga` finds `Lechuga`. Anything less than exact is left unmatched and
offered as a *suggestion* instead — `IBedcaFoodService.suggest` ranks all 957 names in memory
through `food/helpers/NameMatcher` and hands back the best few, and a person picks one with a
PATCH. Similarity alone puts `1 pan integral` on `Pan rallado` and `2 lonchas de jamón serrano`
on `Jamón asado`: close enough to score 100, wrong enough to put a false figure in someone's
diet. A blank is better than a wrong number, so the machine offers and the nutritionist decides.

Importing example-ui.xlsx matches 32 of its 210 ingredients outright; the other 178 come back
from the fix-up endpoint each with its candidates.

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

## Dependencies

Declared in `build.gradle`: Spring Web, Spring Data JPA, Bean Validation, MySQL connector,
Flyway (`flyway-core` + `flyway-mysql`), springdoc-openapi (Swagger UI at `/swagger-ui.html`),
and Lombok (`@Getter`/`@Setter` on the entities only).
