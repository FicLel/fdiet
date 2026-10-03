# USDA SR Legacy refuse extract — licence and source

`refuse.csv` lists every food of the USDA National Nutrient Database for Standard Reference,
Legacy Release (SR Legacy, April 2018) with its **refuse**: the share of the food as purchased
that is not eaten (bone, skin, peel, pits, shells), and what that refuse is.

- Columns: `fdc_id` (FoodData Central id), `ndb_number`, `description`, `refuse_percent`,
  `refuse_description`.
- `ndb_number`, `description`, `refuse_percent` and `refuse_description` are copied without
  change from `FOOD_DES.txt` in `SR-Leg_ASC.zip`; `fdc_id` from `sr_legacy_food.csv` in FoodData
  Central's SR Legacy CSV download. Both archives, their URLs and SHA-256 are in `../manifest.csv`.
  FoodData Central's own CSV and JSON do not carry refuse, which is why the ASCII release is read.
- Licence: FoodData Central data, SR Legacy included, are in the public domain and published under
  **CC0 1.0 Universal**, <https://creativecommons.org/publicdomain/zero/1.0/>. No attribution is
  required; it is given anyway.

> U.S. Department of Agriculture, Agricultural Research Service. FoodData Central: SR Legacy
> (USDA National Nutrient Database for Standard Reference, Legacy Release, April 2018).
> fdc.nal.usda.gov.

fdiet uses it for one figure: a crosswalk row's `edible_portion` is `1 − refuse_percent / 100` of
the SR Legacy food named in its `edible_portion_fdc_id`. A test checks every row against this file.
