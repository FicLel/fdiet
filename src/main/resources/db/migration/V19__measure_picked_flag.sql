-- FD-054: whether the household measure of a recipe ingredient or a logged extra
-- was picked by a person or chosen by the rule.
--
-- * `measure_picked = TRUE` — a person chose `food_measure_id` (the fix-up list, the
--   composer's measure, a PATCH, an extra logged with a measure). It is kept through
--   every re-read and publish while it still weighs the food and unit (FD-039), and a
--   new or changed criterion never replaces it.
-- * `measure_picked = FALSE` — the rule chose it, or there is none. A new or changed
--   diet or global criterion chooses it again in every week it reaches.
--
-- The flag means something only beside a measure. No check says so: the measure's
-- foreign key sets NULL on delete, and MySQL refuses a CHECK on a column with a
-- cascading foreign key (see V14). The services read "picked" as "flagged and a
-- measure is attached".
--
-- Nothing recorded the difference before, so every stored measure starts as picked:
-- a person may have chosen any of them, and keeping one that the rule chose costs
-- nothing but its following new criteria. V20 (a Java migration) then frees the ones
-- the rule chooses today anyway, which no person needs to have picked.

ALTER TABLE recipe_ingredients
    ADD COLUMN measure_picked BOOLEAN NOT NULL DEFAULT FALSE AFTER food_measure_id;

ALTER TABLE extra_foods
    ADD COLUMN measure_picked BOOLEAN NOT NULL DEFAULT FALSE AFTER food_measure_id;

UPDATE recipe_ingredients SET measure_picked = TRUE WHERE food_measure_id IS NOT NULL;
UPDATE extra_foods SET measure_picked = TRUE WHERE food_measure_id IS NOT NULL;

-- A criterion reaches the rows of its food whose measure nobody picked.
CREATE INDEX idx_recipe_ingredients_food_picked
    ON recipe_ingredients (composition_food_id, measure_picked);
CREATE INDEX idx_extra_foods_food_picked
    ON extra_foods (composition_food_id, measure_picked);
