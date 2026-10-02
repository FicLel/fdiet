-- A dish is a description and, beside it, a recipe.
--
-- Until now a dish was one sentence ("Tostada (60 gr) con tomate (80 gr)") read
-- into a name and its ingredients. The patient reads the plate by what it is —
-- "Huevos revueltos" — and what goes into it, and how it is made, belongs to a
-- recipe the nutritionist can write once and pick again for another meal.
--
-- `recipes` holds both kinds:
--
-- * a library recipe (`library = TRUE`) is shared. Every dish that points at it
--   reads the same ingredients and steps, and editing it changes every week that
--   uses it. Its name is how it is found again, so `uk_recipes_library_name`
--   forbids two with the same one — through `library_key`, a generated column
--   that is the name while the recipe is in the library and NULL otherwise, the
--   same trick `diets.active_flag` plays: any number of private recipes may be
--   called "Ensalada".
-- * a private recipe (`library = FALSE`) is one dish's own, written in its cell.
--   It is replaced with the week it belongs to, the way the ingredients used to be.
--
-- `diet_dishes.name` stays and is the description. `recipe_id` is nullable — a
-- dish may be a description only ("Comida libre") — and has no ON DELETE: a
-- library recipe still on somebody's plate is refused, not taken off it.
-- `servings` scales a shared recipe for one patient, so "Huevos revueltos" is
-- written once and served as 1.5 of itself where the week needs it. Nothing is
-- scaled in storage; the figures are multiplied on read.
--
-- Every existing dish becomes the owner of a private recipe carrying its name and
-- the cell it was written as (`raw_text` moves here from the dish), and its
-- ingredients move under that recipe with every food match, measure and range
-- they already carry. `diet_ingredients` is renamed rather than copied, so no row
-- is rewritten; the foreign keys it keeps from V2–V10 keep their old names.

CREATE TABLE recipes (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    name           VARCHAR(255)  NOT NULL,
    steps          VARCHAR(4000) NULL,
    raw_text       VARCHAR(1000) NULL,
    library        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     DATETIME      NOT NULL,
    updated_at     DATETIME      NOT NULL,
    library_key    VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN library THEN name END) STORED,
    source_dish_id BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recipes_library_name UNIQUE (library_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

ALTER TABLE diet_dishes
    ADD COLUMN recipe_id BIGINT       NULL AFTER name,
    ADD COLUMN servings  DECIMAL(6, 2) NOT NULL DEFAULT 1.00 AFTER recipe_id;

INSERT INTO recipes (name, raw_text, library, created_at, updated_at, source_dish_id)
SELECT d.name, d.raw_text, FALSE, NOW(), NOW(), d.id
FROM diet_dishes d;

UPDATE diet_dishes d
    JOIN recipes r ON r.source_dish_id = d.id
SET d.recipe_id = r.id;

ALTER TABLE diet_dishes
    ADD CONSTRAINT fk_diet_dishes_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id),
    DROP COLUMN raw_text;

ALTER TABLE diet_ingredients
    ADD COLUMN recipe_id BIGINT NULL AFTER id;

UPDATE diet_ingredients i
    JOIN diet_dishes d ON d.id = i.dish_id
SET i.recipe_id = d.recipe_id;

ALTER TABLE diet_ingredients
    DROP FOREIGN KEY fk_diet_ingredients_dish;

ALTER TABLE diet_ingredients
    DROP INDEX uk_diet_ingredients_position,
    DROP COLUMN dish_id,
    MODIFY COLUMN recipe_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_recipe_ingredients_position UNIQUE (recipe_id, position),
    ADD CONSTRAINT fk_recipe_ingredients_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipes (id) ON DELETE CASCADE;

RENAME TABLE diet_ingredients TO recipe_ingredients;

ALTER TABLE recipes
    DROP COLUMN source_dish_id;

-- The recipe library is searched by name.
CREATE INDEX idx_recipes_name ON recipes (name);
