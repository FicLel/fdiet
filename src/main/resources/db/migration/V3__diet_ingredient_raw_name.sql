-- An ingredient the food catalogue does not know is kept, not discarded.
--
-- The catalogue is a branded commercial-product dataset keyed on EAN, while a
-- diet is written in ordinary words ("lechuga (80 gr)"), so most ingredients
-- of an imported week match nothing. Dropping them would lose the diet, so
-- `food_item_id` becomes nullable and `raw_name` keeps what the source called
-- the food. A null `food_item_id` is an ingredient still waiting to be matched;
-- the nutritionist attaches the catalogue row afterwards.
--
-- The foreign key stays as it is: it simply permits NULL now.

ALTER TABLE diet_ingredients
    MODIFY food_item_id BIGINT NULL,
    ADD COLUMN raw_name VARCHAR(255) NOT NULL AFTER food_item_id;
