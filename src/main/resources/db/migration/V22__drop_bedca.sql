-- FD-033 phase E: BEDCA leaves the schema.
--
-- Since phase D (V17, V18) every recipe ingredient and journal extra points at
-- `composition_foods` (CIQUAL 2025 / BLS 4.0); V17 set every `bedca_food_id` to
-- NULL and no entity maps the column, so nothing is lost here. The reference
-- tables lost their BEDCA columns in V16. What is left is the two empty columns,
-- their foreign keys and indexes, and the `bedca_foods` table itself.
--
-- The foreign keys go first, then the indexes MySQL kept for them, then the
-- columns, and only then the table they referenced. The names are the ones V4
-- and V6 gave them; V13 renamed `diet_ingredients` to `recipe_ingredients` and
-- kept its constraint names.
--
-- `bedcaFoodId` in a request stays a 400 (`RetiredFields`, decision 24): this
-- removes the data, not the refusal.

ALTER TABLE recipe_ingredients DROP FOREIGN KEY fk_diet_ingredients_bedca;
DROP INDEX idx_diet_ingredients_bedca ON recipe_ingredients;
ALTER TABLE recipe_ingredients DROP COLUMN bedca_food_id;

ALTER TABLE extra_foods DROP FOREIGN KEY fk_extra_foods_bedca;
DROP INDEX idx_extra_foods_bedca ON extra_foods;
ALTER TABLE extra_foods DROP COLUMN bedca_food_id;

DROP TABLE bedca_foods;
