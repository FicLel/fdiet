-- The nutritionist's own weight for a household measure of one food, for every
-- diet: "1 huevo mediano son 58 g", "1 rebanada de pan de molde son 30 g".
--
-- `ref_food_measures` already holds two kinds of row: a published one (a source
-- and a code) and one diet's criterion (`diet_id`). A third kind is added beside
-- them rather than a table of its own, because every caller already weighs an
-- ingredient through `food_measure_id` and the matcher already reads these rows:
--
-- * `global_criterion = TRUE` — the nutritionist's criterion, belonging to no diet
--   and no source. It is reused by every diet of every patient and by library
--   recipes, it is always shown as hers and never as published data, and the
--   reference sync never touches it: the sync addresses rows by `code`, and the
--   check below keeps a criterion without one.
--
-- `criterion_key` is the food, measure and size while the row is a global
-- criterion and NULL otherwise — the `library_key` trick again — so
-- `uk_ref_food_measures_criterion` allows one criterion per food, measure and
-- size, and any number of published or per-diet rows. The check cannot name
-- `diet_id` (MySQL refuses a CHECK on a column with a cascading foreign key), so
-- "no diet" is the service's rule, as the either-or of V8 already is.

ALTER TABLE ref_food_measures
    ADD COLUMN global_criterion BOOLEAN NOT NULL DEFAULT FALSE AFTER diet_id;

ALTER TABLE ref_food_measures
    ADD COLUMN criterion_key VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN global_criterion
        THEN CONCAT(bedca_food_id, ':', measure, ':', COALESCE(size, '')) END) STORED;

ALTER TABLE ref_food_measures
    ADD CONSTRAINT uk_ref_food_measures_criterion UNIQUE (criterion_key);

ALTER TABLE ref_food_measures
    ADD CONSTRAINT ck_ref_food_measures_global
        CHECK (NOT global_criterion
            OR (source_id IS NULL AND code IS NULL AND bedca_food_id IS NOT NULL));

-- The criteria are read by the foods an ingredient list names.
CREATE INDEX idx_ref_food_measures_global ON ref_food_measures (global_criterion, bedca_food_id);
