# USDA Table of Cooking Yields for Meat and Poultry, Release 2 (2014)

U.S. Department of Agriculture, Agricultural Research Service. 2014. *USDA Table of Cooking Yields
for Meat and Poultry, Release 2.* Nutrient Data Laboratory.

- Documentation: <https://www.ars.usda.gov/ARSUserFiles/80400535/Data/retn/USDA_CookingYields_MeatPoultry02.pdf>
- Table: <https://www.ars.usda.gov/ARSUserFiles/80400535/Data/retn/USDA_CookingYields_MeatPoultry02.xlsx>

A work of the United States federal government, and so in the public domain in the United States
(17 U.S.C. § 105). The citation above is asked for by the source and is kept with the figures.

`yield_factors.csv` is a selection of rows, copied without change: `food_label`, `method`,
`yield_pct` and `samples` are the table's *Yield Description*, *Preparation Method*, *Cooking
Yield %* and *n*. `page_ref` names the row by its NDB number, or by its description where the
table gives none. Rows with no `samples` are the 1975 figures the table carries over from AH-102.
`keywords`, `method_keywords` and `note` are fdiet's: which foods a row is offered for (by fdiet's family and Spanish name), and
which Spanish cooking words its method is written as.

A cooking yield is the cooked weight per 100 g raw. fdiet only ever **offers** one — beside an
ingredient weighed raw and matched to a cooked food, or the reverse — and never converts a
quantity by it.
