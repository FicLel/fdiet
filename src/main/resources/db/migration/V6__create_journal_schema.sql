-- The patient's side of the plan.
--
-- A diet says what to eat. These two tables say what was thought of it and what
-- was eaten instead, and they are the only rows in the schema a patient writes.
--
-- WHY A SCORE IS KEYED ON THE SLOT AND NOT ON THE DISH.
-- `PUT /api/diets/{id}` replaces a diet's whole week, so publishing a single
-- edited cell deletes all seventy `diet_dishes` rows and inserts seventy new
-- ones. A score pointing at `diet_dishes.id` would be taken with them by the
-- cascade every time the nutritionist touched anything — the patient's whole
-- record wiped by an edit to one breakfast. The slot
-- (diet, day, meal, position) survives a republish, and is already what
-- `uk_diet_meals_slot` treats as the identity of a place in the week.
--
-- The cost is stated plainly: if a republish puts a different dish in the slot,
-- the score stays and now describes that one. Losing every score on every
-- publish is worse, and `scored_at` is here so the two can be told apart.

CREATE TABLE dish_scores (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    diet_id     BIGINT      NOT NULL,
    day_of_week VARCHAR(16) NOT NULL,
    meal_type   VARCHAR(24) NOT NULL,
    dish_index  INT         NOT NULL,
    score       INT         NOT NULL,
    scored_at   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    -- One score per plate. Re-scoring writes over the row rather than adding a
    -- second opinion of the same dish.
    CONSTRAINT uk_dish_scores_slot UNIQUE (diet_id, day_of_week, meal_type, dish_index),
    -- The 1-5 the design offers, restated where a bad write cannot get past it.
    CONSTRAINT ck_dish_scores_range CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT fk_dish_scores_diet
        FOREIGN KEY (diet_id) REFERENCES diets (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Something eaten that the plan did not prescribe.
--
-- `raw_name` is what the patient logged, always kept, exactly as
-- `diet_ingredients.raw_name` keeps what the diet called a food. The two
-- catalogue references are both nullable and only ever one of them is set: a
-- branded product is the usual answer here, since a patient logging an extra is
-- normally holding a wrapper, but the composition database is offered too. Both
-- null means nothing was matched, and then the row counts towards nothing but
-- is still on the record — the same rule the week already follows.
CREATE TABLE extra_foods (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    diet_id       BIGINT         NOT NULL,
    day_of_week   VARCHAR(16)    NOT NULL,
    raw_name      VARCHAR(255)   NOT NULL,
    quantity      DECIMAL(10, 2) NOT NULL,
    unit          VARCHAR(32)    NOT NULL,
    bedca_food_id BIGINT         NULL,
    food_item_id  BIGINT         NULL,
    logged_at     DATETIME       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_extra_foods_diet
        FOREIGN KEY (diet_id) REFERENCES diets (id) ON DELETE CASCADE,
    -- A catalogue food an entry points at must not be deletable underneath it,
    -- so neither of these cascades.
    CONSTRAINT fk_extra_foods_bedca
        FOREIGN KEY (bedca_food_id) REFERENCES bedca_foods (id),
    CONSTRAINT fk_extra_foods_item
        FOREIGN KEY (food_item_id) REFERENCES food_items (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The journal is read one week at a time, and a day at a time within it.
CREATE INDEX idx_dish_scores_diet ON dish_scores (diet_id);
CREATE INDEX idx_extra_foods_day ON extra_foods (diet_id, day_of_week);
-- Back the two food foreign keys.
CREATE INDEX idx_extra_foods_bedca ON extra_foods (bedca_food_id);
CREATE INDEX idx_extra_foods_item ON extra_foods (food_item_id);
