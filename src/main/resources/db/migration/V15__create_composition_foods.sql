-- The open composition tables (FD-033 phase B): CIQUAL 2025 (ANSES) and BLS 4.0
-- (Max Rubner-Institut), both CC BY 4.0, in one table beside `bedca_foods`.
--
-- One table rather than two so that every consumer that will point at a food
-- (recipe ingredients, extras, rations, measures — phase C) points at one id
-- column. `source` says which table a row came from and `source_code` is that
-- table's own code as published (CIQUAL's numeric `alim_code`, BLS's `C131000`);
-- `uk_composition_foods_source_code` makes the pair the key a sync writes over,
-- so a re-sync updates in place and an id never changes.
--
-- Values are stored as published, each with its unit, the way `bedca_foods`
-- stores them. A value the source qualifies rather than measures (`traces`,
-- `< 0,2`, `<LOQ`, `<LOD`, `TR`, `-`) is NULL, never 0: the upstream file in
-- reference-data/composition/ is the record of what was written. Energy is the
-- source's kcal figure; 145 CIQUAL foods publish none and stay NULL (tracked in
-- composition-es/ciqual_no_energy.csv).
--
-- The `name_*` and `edible_portion*` columns come from fdiet's crosswalk
-- (composition-es/links.csv, CC BY 4.0), not from either source: the Spanish name a
-- diet is matched on, its aliases, which row answers a name two rows share,
-- whether a person approved the row, and the edible fraction read from the USDA
-- SR Legacy food named in `edible_portion_fdc_id`. NULL on every food the
-- crosswalk does not name yet. A NULL edible portion refuses a gross weight.

CREATE TABLE composition_foods (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    source                 VARCHAR(16)   NOT NULL,
    source_code            VARCHAR(32)   NOT NULL,
    name_original          VARCHAR(255)  NOT NULL,
    name_en                VARCHAR(255)  NULL,
    food_group_code        VARCHAR(16)   NULL,

    name_es                VARCHAR(255)  NULL,
    name_aliases           VARCHAR(1000) NULL,
    name_preferred         BOOLEAN       NOT NULL DEFAULT FALSE,
    name_reviewed          BOOLEAN       NOT NULL DEFAULT FALSE,
    edible_portion         DECIMAL(8, 6) NULL,
    edible_portion_fdc_id  INT           NULL,

    energy                 DECIMAL(14, 6) NULL,
    energy_unit            VARCHAR(16)    NULL,
    protein                DECIMAL(14, 6) NULL,
    protein_unit           VARCHAR(16)    NULL,
    fat                    DECIMAL(14, 6) NULL,
    fat_unit               VARCHAR(16)    NULL,
    saturated_fat          DECIMAL(14, 6) NULL,
    saturated_fat_unit     VARCHAR(16)    NULL,
    carbohydrates          DECIMAL(14, 6) NULL,
    carbohydrates_unit     VARCHAR(16)    NULL,
    sugars                 DECIMAL(14, 6) NULL,
    sugars_unit            VARCHAR(16)    NULL,
    fiber                  DECIMAL(14, 6) NULL,
    fiber_unit             VARCHAR(16)    NULL,
    water                  DECIMAL(14, 6) NULL,
    water_unit             VARCHAR(16)    NULL,
    sodium                 DECIMAL(14, 6) NULL,
    sodium_unit            VARCHAR(16)    NULL,
    potassium              DECIMAL(14, 6) NULL,
    potassium_unit         VARCHAR(16)    NULL,
    calcium                DECIMAL(14, 6) NULL,
    calcium_unit           VARCHAR(16)    NULL,
    iron                   DECIMAL(14, 6) NULL,
    iron_unit              VARCHAR(16)    NULL,
    cholesterol            DECIMAL(14, 6) NULL,
    cholesterol_unit       VARCHAR(16)    NULL,
    vitamin_c              DECIMAL(14, 6) NULL,
    vitamin_c_unit         VARCHAR(16)    NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_composition_foods_source_code UNIQUE (source, source_code),
    CONSTRAINT ck_composition_foods_source CHECK (source IN ('CIQUAL', 'BLS')),
    CONSTRAINT ck_composition_foods_edible_portion
        CHECK (edible_portion IS NULL OR (edible_portion > 0 AND edible_portion <= 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The Spanish name is what a diet is matched and searched on.
CREATE INDEX idx_composition_foods_name_es ON composition_foods (name_es);
