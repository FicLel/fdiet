-- FD-033 phase D: recipe ingredients and the journal's extras point at a
-- composition food (CIQUAL 2025 / BLS 4.0, `composition_foods`) instead of a
-- BEDCA food.
--
-- * `composition_food_id` is added to both tables, with its foreign key and the
--   index that backs it. It has no `ON DELETE`: a composition food is never
--   deleted by a sync (rows are written over by `(source, source_code)`), and one
--   that somebody removed by hand should fail loudly rather than unmatch a week.
-- * Every `bedca_food_id` is reset to NULL (decisions 2 and 19). Old BEDCA ids are
--   never carried over to the new column: a BEDCA food and a CIQUAL/BLS food are
--   different analyses, and pointing one at the other by id would be a guess.
--   The columns, their foreign keys and `bedca_foods` itself stay until phase E
--   drops them; nothing maps them any more.
-- * `raw_name`, `food_item_id` (a branded match) and `food_measure_id` are left
--   alone. A measure that no longer fits the food the ingredient is matched to
--   is released by V18 and re-chosen by the resolver on the next save.
-- * An ingredient or extra points at one food or none, never two: the check
--   restates in the schema what the services already refuse.
--
-- V18 (a Java migration, `db.migration.V18__rematch_ingredients_by_composition_name`)
-- then re-matches what this one reset, by exact Spanish name or alias only.

-- The index backs the foreign key and answers "which recipes use this food";
-- declared first so MySQL does not add an unnamed one of its own for the key.
ALTER TABLE recipe_ingredients
    ADD COLUMN composition_food_id BIGINT NULL AFTER bedca_food_id,
    ADD INDEX idx_recipe_ingredients_composition_food (composition_food_id),
    ADD CONSTRAINT fk_recipe_ingredients_composition_food
        FOREIGN KEY (composition_food_id) REFERENCES composition_foods (id),
    ADD CONSTRAINT ck_recipe_ingredients_one_food
        CHECK (food_item_id IS NULL OR composition_food_id IS NULL);

ALTER TABLE extra_foods
    ADD COLUMN composition_food_id BIGINT NULL AFTER bedca_food_id,
    ADD INDEX idx_extra_foods_composition_food (composition_food_id),
    ADD CONSTRAINT fk_extra_foods_composition_food
        FOREIGN KEY (composition_food_id) REFERENCES composition_foods (id),
    ADD CONSTRAINT ck_extra_foods_one_food
        CHECK (food_item_id IS NULL OR composition_food_id IS NULL);

UPDATE recipe_ingredients SET bedca_food_id = NULL WHERE bedca_food_id IS NOT NULL;
UPDATE extra_foods SET bedca_food_id = NULL WHERE bedca_food_id IS NOT NULL;
