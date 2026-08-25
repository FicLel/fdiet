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
`SERVER_PORT` and `FOOD_CSV_PATH`.

## Architecture

fdiet is a Spring Boot backend organized per bounded context under `src/main/java/com/fdiet/<feature>/`.
The only feature module is `food`; `common` holds the shared transport types.

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
  delimiters/line breaks, doubled quotes, leading BOM). Reads the path in `fdiet.food.csv-path`.

### Importing

`Category` and `SubCategory` use the ids that ship with the CSV (`IdCategoria`,
`IdSubcategoria`) as assigned primary keys — subcategory ids are unique across the whole file.
Food items are keyed on their EAN, which is unique in the CSV, so `POST /api/food/sync` skips
rows already stored and can be re-run safely.

The current fooddata.csv only fills 19 of the `food_items` columns; the rest (market share,
manufacturer, subbrand, portion size, sodium, fibre, …) are kept for richer exports of the same
dataset and stay null after an import.

## Persistence

MySQL via `com.mysql:mysql-connector-j`. **Flyway owns the schema** — migrations live in
`src/main/resources/db/migration` and run at startup. `spring.jpa.hibernate.ddl-auto=validate`,
so Hibernate checks the entities against the migrated schema and never emits DDL itself: after
changing an entity, add a migration to match or startup fails.

## Dependencies

Declared in `build.gradle`: Spring Web, Spring Data JPA, Bean Validation, MySQL connector,
Flyway (`flyway-core` + `flyway-mysql`), springdoc-openapi (Swagger UI at `/swagger-ui.html`),
and Lombok (`@Getter`/`@Setter` on the entities only).
