-- Food catalogue schema.
--
-- `food_category` and `food_subcategory` use the ids that ship with
-- fooddata.csv (IdCategoria / IdSubcategoria) as their primary keys, so an
-- import is repeatable and never renumbers anything. Subcategory ids are
-- unique across the whole file, so each one maps to exactly one category.

CREATE TABLE food_category (
    id       BIGINT       NOT NULL,
    category VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE food_subcategory (
    id          BIGINT       NOT NULL,
    subcategory VARCHAR(255) NOT NULL,
    category_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_food_subcategory_category
        FOREIGN KEY (category_id) REFERENCES food_category (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE food_items (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    category               BIGINT         NULL,
    subcategory            BIGINT         NULL,
    year_value             INT            NULL,
    source_name            VARCHAR(255)   NULL,
    market_share_total_ean DECIMAL(18, 10) NULL,
    ean                    VARCHAR(32)    NOT NULL,
    commercial_name        VARCHAR(500)   NULL,
    manufacturer           VARCHAR(255)   NULL,
    brand                  VARCHAR(255)   NULL,
    subbrand               VARCHAR(255)   NULL,
    legal_name             VARCHAR(500)   NULL,
    ingredients            LONGTEXT       NULL,
    portion_size_g         DECIMAL(10, 2) NULL,
    energy_kj              DECIMAL(10, 2) NULL,
    energy_kcal            DECIMAL(10, 2) NULL,
    fat_g                  DECIMAL(10, 2) NULL,
    saturated_fat_g        DECIMAL(10, 2) NULL,
    carbohydrates_g        DECIMAL(10, 2) NULL,
    sugars_g               DECIMAL(10, 2) NULL,
    proteins_g             DECIMAL(10, 2) NULL,
    salt_g                 DECIMAL(10, 2) NULL,
    sodium_g               DECIMAL(10, 2) NULL,
    monounsaturated_fat_g  DECIMAL(10, 2) NULL,
    polyunsaturated_fat_g  DECIMAL(10, 2) NULL,
    starch_g               DECIMAL(10, 2) NULL,
    fiber_g                DECIMAL(10, 2) NULL,
    polyols_g              DECIMAL(10, 2) NULL,
    sweeteners             VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_food_items_ean UNIQUE (ean),
    CONSTRAINT fk_food_items_category
        FOREIGN KEY (category) REFERENCES food_category (id),
    CONSTRAINT fk_food_items_subcategory
        FOREIGN KEY (subcategory) REFERENCES food_subcategory (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Supports ordering and prefix lookups on the name search. A `%term%` search
-- still scans, but the collation is case-insensitive so no LOWER() is needed.
CREATE INDEX idx_food_items_commercial_name ON food_items (commercial_name);
CREATE INDEX idx_food_items_brand ON food_items (brand);
