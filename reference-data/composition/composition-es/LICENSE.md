# composition-es — fdiet's Spanish names for CIQUAL and BLS foods

**Licence: Creative Commons Attribution 4.0 International (CC BY 4.0)**,
<https://creativecommons.org/licenses/by/4.0/>. Copyright fdiet contributors.

> fdiet composition-es crosswalk (2026). CC BY 4.0. Links Spanish food names to foods of the
> CIQUAL 2025 table (ANSES, CC BY 4.0) and of BLS 4.0 (Max Rubner-Institut, CC BY 4.0), with
> edible portions derived from USDA SR Legacy (CC0 1.0).

It holds no BEDCA values: the Spanish words are fdiet's, and no column is keyed on a BEDCA id.

## `links.csv`

One row per food a Spanish name points at.

| Column | Meaning |
| --- | --- |
| `source` | `CIQUAL` or `BLS` |
| `source_code` | the food's code as the source publishes it (CIQUAL `alim_code`, BLS `BLS Code`) |
| `name_es` | the Spanish name, written head first like `Pollo, pechuga, plancha` |
| `aliases` | other ways a diet writes the same food, `;`-separated (`AOVE`, `tomate cherry`) |
| `preferred` | when two rows claim the same name or alias, the one that answers it; exactly one of them may be preferred |
| `edible_portion` | the edible fraction of the food as purchased, `1 − refuse/100` of the SR Legacy food below; blank when none fits |
| `edible_portion_fdc_id` | the SR Legacy food (FoodData Central id) the edible portion was taken from |
| `reviewed` | `true` once a person has approved the row; the 122 rows of phase B were approved by the project owner on 2026-10-03, and the one added in phase C (CIQUAL 13716) awaits approval |
| `note` | why a choice was made |

Matching on these names is exact (case and accents ignored): a name or alias either names a food or
it does not. CIQUAL answers first; a BLS row is used where CIQUAL has no same-food equivalent, or
where only BLS publishes the state written (`plancha`, `asado`) or an energy figure.

**Approved 2026-10-03.** Every row was pre-filled by machine from the sources' English and French names
(the FD-033 spike's method), checked by name, and approved by the project owner on 2026-10-03;
`reviewed` turns `true` only when a person approves the row. The edible portion of an SR Legacy food
describes the food as purchased in the US (bone-in, in shell, with peel): a reviewer should confirm
it describes the purchase the row means. A blank edible portion still refuses a gross weight.

Scope today: the foods of `example-ui.xlsx` "Dieta 1" and the foods fdiet referenced (then in the
retired BEDCA table) on 2026-10-03 (FD-033 phase B). Not linked on purpose, because neither source has a defensible
equivalent: néctar de ciruela, queso fresco de Burgos, hummus casero, bacalao desalado, salsa de soja
baja en sodio. The rest of both tables is FD-036.

Phase C (2026-10-03) added one row, `reviewed=false` until approved: piña en su jugo, which phase B
had listed as without an equivalent, is CIQUAL 13716 (pineapple in its own juice, canned, drained). It
is the food the 5 al día row of that name now names; see `product/spikes/FD-033-C-rekey-mapping.md`.
Every food a reference CSV names (`composition_source`, `composition_code`) is a row of this file.

## `ciqual_no_energy.csv`

The CIQUAL 2025 foods whose energy cell is not a number (`-` for 143 of them, `traces` for 2), with
what was published. They are imported with a blank energy — never an invented one — and are tracked
here for FD-037 (an open source for their energy). A test keeps the list equal to the XLSX.
