-- Diet schema.
--
-- There is one active diet at a time and a record of the archived ones behind
-- it. `diets.active_flag` is a generated column holding 1 while the row is
-- active and NULL once it is archived; MySQL lets a unique index hold any
-- number of NULLs, so `uk_diets_active` permits every archived diet but only a
-- single active one. Hibernate does not map that column, so no insert writes
-- it.
--
-- A dish ingredient always points at a row of `food_items`: that reference is
-- where the nutrition figures of the week come from, so it is NOT NULL and its
-- foreign key does not cascade — a catalogue product a diet uses must not be
-- deletable.

CREATE TABLE diets (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    started_on  DATE         NOT NULL,
    ended_on    DATE         NULL,
    created_at  DATETIME     NOT NULL,
    active_flag TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_diets_active UNIQUE (active_flag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- One meal per day and slot, the same rule the in-memory diet enforces.
CREATE TABLE diet_meals (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    diet_id     BIGINT       NOT NULL,
    day_of_week VARCHAR(16)  NOT NULL,
    meal_type   VARCHAR(24)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_diet_meals_slot UNIQUE (diet_id, day_of_week, meal_type),
    CONSTRAINT fk_diet_meals_diet
        FOREIGN KEY (diet_id) REFERENCES diets (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE diet_dishes (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    meal_id  BIGINT       NOT NULL,
    name     VARCHAR(255) NOT NULL,
    position INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_diet_dishes_position UNIQUE (meal_id, position),
    CONSTRAINT fk_diet_dishes_meal
        FOREIGN KEY (meal_id) REFERENCES diet_meals (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE diet_ingredients (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    dish_id      BIGINT        NOT NULL,
    food_item_id BIGINT        NOT NULL,
    quantity     DECIMAL(10, 2) NOT NULL,
    unit         VARCHAR(32)   NOT NULL,
    position     INT           NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_diet_ingredients_position UNIQUE (dish_id, position),
    CONSTRAINT fk_diet_ingredients_dish
        FOREIGN KEY (dish_id) REFERENCES diet_dishes (id) ON DELETE CASCADE,
    CONSTRAINT fk_diet_ingredients_food
        FOREIGN KEY (food_item_id) REFERENCES food_items (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Orders the history listing.
CREATE INDEX idx_diets_started_on ON diets (started_on);
-- Answers "which diets use this product", and backs the food foreign key.
CREATE INDEX idx_diet_ingredients_food ON diet_ingredients (food_item_id);
