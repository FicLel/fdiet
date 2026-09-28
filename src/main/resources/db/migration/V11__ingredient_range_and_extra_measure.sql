-- A quantity written as a range, and a logged extra that can be weighed the way
-- the week's ingredients are.
--
-- `quantity_max` is the upper bound of "2-3 lonchas de pavo (40-60 gr)":
-- `quantity` keeps the lower one. Reading used to keep only the last number, which
-- silently decided on 60 g for the nutritionist. A ranged ingredient is weighed
-- by nobody until a person confirms one value, which clears the bound — a range
-- priced at either end, or at a midpoint, would be a choice the text never made.
--
-- `extra_foods` gains the three columns V10 gave `diet_ingredients`, so "1 cdta
-- de aceite" logged beside the plan is weighed by the same household measure
-- that weighs it inside the plan. One rule for both, or the day's plan and that
-- day's extras disagree about the same spoon.

ALTER TABLE diet_ingredients
    ADD COLUMN quantity_max DECIMAL(10, 2) NULL AFTER quantity;

ALTER TABLE extra_foods
    ADD COLUMN state           VARCHAR(16) NULL AFTER unit,
    ADD COLUMN portion_size    VARCHAR(8)  NULL AFTER state,
    ADD COLUMN food_measure_id BIGINT      NULL AFTER food_item_id,
    ADD CONSTRAINT fk_extra_foods_measure
        FOREIGN KEY (food_measure_id) REFERENCES ref_food_measures (id) ON DELETE SET NULL;

CREATE INDEX idx_extra_foods_measure ON extra_foods (food_measure_id);
