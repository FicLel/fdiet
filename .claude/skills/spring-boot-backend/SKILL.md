---
name: spring-boot-backend
description: Java 21 + Spring Boot development rules for fdiet's backend — KISS, DRY, SOLID, batched data access with no nested loops over database results, O(n) worst case, and a Flyway-owned schema that is extended, never restructured. Use whenever writing, refactoring or reviewing Java under src/main/java or src/test/java, adding an endpoint, service, repository, entity, DTO or migration, or chasing a slow query / N+1.
---

# Spring Boot backend — how code is written here

The root `CLAUDE.md` is the source of truth for *what* each context does and *why*. This skill is
*how* the Java is written. Read the relevant `CLAUDE.md` section and the neighbouring classes
before writing anything, and match their naming, comment density and idiom.

## 1. Structure (do not change it)

- One package per bounded context: `com.fdiet.<feature>/{controller,service,repository,model,dto,mapper,helpers,domain}`.
- `controller -> service -> repository`, constructor injection only (no `@Autowired` fields).
- **Controllers are dumb**: forward arguments, return what comes back. No `Pageable` building, no
  logic, no entities.
- **DTOs cross layer boundaries**; `PageDto` wraps paged results so `Page` stays in the service.
- **One owning service per table.** Only it touches that repository. Another service reaches the
  table through the owner's interface (`IBedcaFoodService.entitiesByIds`, `IPatientService.entityById`).
  Service-to-service may pass entities.
- Services, mappers and helpers are injected through an interface (`IXxxService`, `IXxxMapper`, …),
  as in `diet/`, `alternative/`, `patient/` and `reference/`. New classes follow that; don't retrofit
  the older concrete `food/` ones unless asked.
- Lombok `@Getter`/`@Setter` on entities only. DTOs are records where the neighbours are records.

## 2. Principles, made concrete

**KISS**
- The simplest thing that is correct. No speculative abstraction, no generic framework for one
  caller, no strategy/factory until there are two real variants.
- Prefer a derived query method (`findByPatientIdAndStatus`) over `@Query`, `@Query` over Criteria,
  and Criteria only when the query is genuinely dynamic.
- Small methods with one reason to exist; early returns over nested `if`s.
- **No file over 500 lines.** Split by responsibility (helper, mapper, a service owning a
  sub-concern) before a class reaches it. A file already over it must not grow; split the part
  you touch.
- **No magic strings or numbers.** A status, unit, code, threshold or key that code compares
  against or uses twice is an enum or a named constant in the class that owns the concept.

**DRY**
- Before writing a helper, grep for one: `PortionScaler`, `NameMatcher`, `NutritionService`,
  `CachedLookup`, `ReferenceMatcher`, `DataReader` already exist. One answer per question — two copies
  of "what does a millilitre weigh" will drift.
- Enumerations that name things once (`Nutrient`, `HouseholdMeasure`, `FoodCategory`) are looped
  over, not repeated field by field.
- Don't DRY away a difference that is real: two methods that look alike but answer different
  questions stay two.

**SOLID**
- *S*: a service owns its tables and nothing else; parsing, matching, arithmetic live in helpers.
- *O*: extend by adding (an enum constant, a new implementation), not by editing `switch`es in five places.
- *L*: an implementation honours its interface's contract — including what null means.
- *I*: narrow interfaces; a caller that needs one lookup shouldn't depend on the import methods.
- *D*: depend on the `I…` interface, never the concrete class, across services.

## 3. Data access and complexity — the hard rules

Target **O(n) worst case** in the size of the data being processed, and a **constant number of
queries per request**, independent of n.

1. **No query inside a loop.** Never call a repository (or a service that queries) per element.
   Collect the ids/keys first, fetch once (`findAllById`, `...In(Collection)`, `entitiesByIds`,
   `entitiesByName`), then work in memory.
2. **No nested loops to join data.** Index one side into a `Map` (O(n)), then probe it (O(1)):
   ```java
   Map<Long, BedcaFood> foodsById = bedcaFoodService.entitiesByIds(ids).stream()
           .collect(Collectors.toMap(BedcaFood::getId, Function.identity()));
   for (RecipeIngredient ing : ingredients) {
       BedcaFood food = foodsById.get(ing.getBedcaFoodId());   // O(1), not a scan
   }
   ```
   Group with `Collectors.groupingBy` rather than filtering a list once per parent. Use `Set` for
   membership, never `List.contains` in a loop. `EnumMap` for enum keys.
3. **Avoid N+1 on associations.** Use `@EntityGraph(attributePaths = …)` on the repository method
   (see `DietRepository`) or a `JOIN FETCH`, not lazy access inside a loop. Never fetch-join two
   collections in one query (cartesian product) — fetch the second in a separate batched query.
4. **Let the database do set work**: counts (`countBy…`, `existsBy…`), filtering, ordering and
   paging belong in SQL, not in `findAll()` + streams. The deliberate exception is a small, bounded
   table read whole once per request (the 957 BEDCA rows via `entitiesAll()`) — say so in a comment.
5. **Bulk writes are batched**: `saveAll`, or the `EntityManager` fragment pattern
   (`FoodItemBatchRepository`) with periodic `flush()/clear()` for large imports.
6. **Paging**: unbounded lists from user-facing endpoints are paged unless the data is a known
   handful (patients).
7. **Caches hold ids, never entities** — an entity cached across a transaction is detached.
8. `@Transactional(readOnly = true)` on read paths; write transactions as short as possible, no
   remote calls or file I/O inside them unless unavoidable.
9. When a sort is required, O(n log n) is acceptable; say why if a step goes beyond O(n). Any
   O(n²) needs an explicit justification (and a bound on n) in a comment, or it is a bug.

Self-check before finishing: for every loop, ask "does this body touch the database or scan
another collection?" If yes, restructure.

## 4. Database structure — extend, never reshape

- **Flyway owns the schema.** `ddl-auto=validate`; Hibernate never emits DDL. Any entity change
  needs a new migration `V<next>__<snake_case_description>.sql` in `src/main/resources/db/migration`.
- **Never edit an applied migration.** Never renumber. Add the next one.
- Keep the existing table/column naming (`snake_case`, plural tables, `fk_<table>_<target>`,
  `uk_<table>_<what>`, `idx_<table>_<cols>`), the existing ids and existing relations. Don't
  rename or drop tables/columns, move data between contexts, or change keys unless the user
  explicitly asks.
- Invariants that matter live **in the schema too** (unique indexes, FKs, generated columns like
  `active_flag` / `library_key`). Delete-protection is an FK without `ON DELETE`, translated to a
  400 by the service — not a cascade.
- Store values as published (BEDCA: value + unit). Derived figures (kcal from kJ, scaled portions,
  categories, rations) are computed on read, never written back.
- Add an index for every new column you filter or join on.
- After a migration, update the migrations table in `CLAUDE.md`.

## 5. Errors, validation, API

- Bean Validation on request DTOs (`@Valid`, `@NotNull`, …). Domain-level refusals are exceptions
  mapped to 400/404 the way the existing `…NotFoundException`s are.
- Null is meaningful in this codebase ("unknown", "not matched yet"). Never replace it with an
  invented default; a blank beats a wrong number.
- Totals travel with their counts. If you add an aggregate, add the counts it was computed over.

## 6. Verifying

- `gradlew.bat build` must be clean; run focused tests with
  `gradlew.bat test --tests "com.fdiet.<feature>...."`.
- Pure helpers and services get unit tests (Mockito for the interfaces) next to the existing ones
  under `src/test/java/com/fdiet/<feature>/`.
- **Warning:** `FdietApplicationTests` boots the context and runs Flyway against the real `.env`
  database. A new migration lands in the dev DB on the first full test run — tell the user before
  running the full suite after adding one, and prefer `--tests` on unit tests first.
