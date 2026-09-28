-- What a written ingredient says beyond its name and its number.
--
-- `state` is the raw/cooked word the nutritionist wrote ("arroz (70 g crudo)"),
-- which reading the sentence used to throw away; a raw weight priced as boiled
-- rice is roughly a threefold error. `portion_size` is "pequeña"/"mediana"/
-- "grande", which decides which household-measure row can weigh "1 pera pequeña".
--
-- `food_measure_id` is the household measure that weighs an ingredient written
-- in one ("1 cdta AOVE", "1 kiwi"). Attaching one is a match, and follows the
-- matching rule: automatic only when it is unambiguous, otherwise offered and
-- picked by a person. Deleting a diet's own measure leaves the ingredient
-- unmeasured rather than weighed by something else.

ALTER TABLE diet_ingredients
    ADD COLUMN state           VARCHAR(16) NULL AFTER unit,
    ADD COLUMN portion_size    VARCHAR(8)  NULL AFTER state,
    ADD COLUMN food_measure_id BIGINT      NULL AFTER bedca_food_id,
    ADD CONSTRAINT fk_diet_ingredients_measure
        FOREIGN KEY (food_measure_id) REFERENCES ref_food_measures (id) ON DELETE SET NULL;

CREATE INDEX idx_diet_ingredients_measure ON diet_ingredients (food_measure_id);
