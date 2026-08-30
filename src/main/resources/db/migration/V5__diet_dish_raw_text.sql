-- The cell as the nutritionist wrote it.
--
-- A dish is stored split — a name, then an ingredient per `+`, each with one
-- quantity — and that split cannot be undone. `MealTextParser` keeps the last
-- quantity of a fragment and strips the brackets out of the name, so
-- "Tostada de pan integral (60 gr) con tomate rallado (80 gr)" comes back as
-- "Tostada de pan integral con tomate rallado (80 gr) (60 gr)": the same
-- ingredients, a different sentence.
--
-- That matters because `PUT /api/diets/{id}` replaces the whole week, so an
-- editor changing one cell has to send back the other sixty-nine. Rebuilding
-- them would rewrite cells nobody touched. `raw_text` holds the line itself,
-- the way `diet_ingredients.raw_name` already holds the ingredient as written,
-- and the round trip stops losing anything.
--
-- Nullable on purpose, and never invented: a dish assembled by a caller that
-- had no sentence to begin with has no raw text, and a reconstruction is not
-- one. Null means "what was written is not known", which is the truth for
-- every dish stored before this migration.

ALTER TABLE diet_dishes
    ADD COLUMN raw_text VARCHAR(1000) NULL AFTER name;
