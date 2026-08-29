-- BEDCA: the generic-food half of the catalogue.
--
-- `food_items` is a branded commercial-product dataset keyed on EAN, and a
-- diet is written in ordinary words ("lechuga (80 gr)"), which is why almost
-- nothing in an imported week matches it. `bedca_foods` is the other half:
-- 957 generic Spanish foods from AESAN/BEDCA v1.0, the names a diet actually
-- uses, each with its composition per 100 g of edible portion.
--
-- The ids are the ones the source assigns (`f_id`), like `food_category` and
-- `food_subcategory` reuse theirs, so a sync is repeatable and renumbers
-- nothing.
--
-- Every nutrient is a value column and a unit column, because the source
-- publishes the unit per value and its terms of use forbid normalising it:
-- energy is kJ for 947 of the 957 foods and kcal for 8, and carbohydrate,
-- fibre and water each have a stray milligram row. Anything derived from
-- these figures (kcal from kJ, a portion scaled off the per-100 g value) is
-- computed when it is read and never written back.
--
-- Attribution — required wherever these figures are shown, see
-- BEDCA-ATTRIBUTION.txt:
--   AESAN/BEDCA Base de Datos Española de Composición de Alimentos v1.0 (2010)

CREATE TABLE bedca_foods (
    id                 BIGINT       NOT NULL,
    name               VARCHAR(255) NOT NULL,
    english_name       VARCHAR(255) NULL,
    scientific_name    VARCHAR(255) NULL,
    food_group         VARCHAR(255) NULL,
    food_subgroup      VARCHAR(255) NULL,
    origin             VARCHAR(16)  NULL,
    edible_portion     DECIMAL(8, 6) NULL,

    energy             DECIMAL(14, 6) NULL,
    energy_unit        VARCHAR(16)    NULL,
    protein            DECIMAL(14, 6) NULL,
    protein_unit       VARCHAR(16)    NULL,
    fat                DECIMAL(14, 6) NULL,
    fat_unit           VARCHAR(16)    NULL,
    saturated_fat      DECIMAL(14, 6) NULL,
    saturated_fat_unit VARCHAR(16)    NULL,
    carbohydrates      DECIMAL(14, 6) NULL,
    carbohydrates_unit VARCHAR(16)    NULL,
    sugars             DECIMAL(14, 6) NULL,
    sugars_unit        VARCHAR(16)    NULL,
    fiber              DECIMAL(14, 6) NULL,
    fiber_unit         VARCHAR(16)    NULL,
    water              DECIMAL(14, 6) NULL,
    water_unit         VARCHAR(16)    NULL,
    sodium             DECIMAL(14, 6) NULL,
    sodium_unit        VARCHAR(16)    NULL,
    potassium          DECIMAL(14, 6) NULL,
    potassium_unit     VARCHAR(16)    NULL,
    calcium            DECIMAL(14, 6) NULL,
    calcium_unit       VARCHAR(16)    NULL,
    iron               DECIMAL(14, 6) NULL,
    iron_unit          VARCHAR(16)    NULL,
    cholesterol        DECIMAL(14, 6) NULL,
    cholesterol_unit   VARCHAR(16)    NULL,
    vitamin_c          DECIMAL(14, 6) NULL,
    vitamin_c_unit     VARCHAR(16)    NULL,

    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Backs the exact-name lookup the diet resolves through. The collation is
-- case- and accent-insensitive, so "lechuga" finds "Lechuga" with no LOWER().
CREATE INDEX idx_bedca_foods_name ON bedca_foods (name);

-- An ingredient can be matched to either half of the catalogue: a branded
-- product when the diet names one, a generic food the rest of the time. Both
-- are nullable and both may be null at once — that is an ingredient still
-- waiting to be matched, kept as written rather than dropped.
ALTER TABLE diet_ingredients
    ADD COLUMN bedca_food_id BIGINT NULL AFTER food_item_id,
    ADD CONSTRAINT fk_diet_ingredients_bedca
        FOREIGN KEY (bedca_food_id) REFERENCES bedca_foods (id);

-- Answers "which diets use this food", and backs the foreign key above.
CREATE INDEX idx_diet_ingredients_bedca ON diet_ingredients (bedca_food_id);
