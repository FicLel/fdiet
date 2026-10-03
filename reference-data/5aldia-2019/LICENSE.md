# Licence of the files in this folder

`rations.csv` and `food_measures.csv` are derived from **Tabla 4** of:

> Russolillo G, Baladia E, Moñino M, et al. *Establecimiento del tamaño de raciones de consumo de
> frutas y hortalizas para su uso en guías alimentarias en el entorno español: propuesta del
> Comité Científico de la Asociación 5 al día.* Rev Esp Nutr Hum Diet. 2019;23(4):205-221.
> doi:[10.14306/renhyd.23.4.628](https://doi.org/10.14306/renhyd.23.4.628)

The article is published under **Creative Commons Attribution-ShareAlike 4.0 International
(CC BY-SA 4.0)**, <https://creativecommons.org/licenses/by-sa/4.0/>. These two files are an
adaptation of it and are released under the same licence.

What was done to the table:

- each row keeps the food, the household measure and the net and gross weights as printed;
- `composition_source` and `composition_code` link a food to the food of the open composition tables
  (CIQUAL 2025 or BLS 4.0) fdiet matches it to, where one exists — columns added by fdiet, not part of
  the article;
- `food_measures.csv` restates the household measure of each linked food as a measure, a size and a
  count (`3 Uds. medianas` → `UNIDAD`, `MEDIUM`, `3`), with the same weight. Measures given as a
  range of pieces (`6-8 Uds.`) or in a word the vocabulary does not carry (`2 pencas`) are left out.

## Changes

- **2026-10-03 (fdiet, FD-033 phase C):** the `bedca_food_id` column (a BEDCA food id, added by
  fdiet) was replaced by `composition_source` + `composition_code`, naming a CIQUAL 2025 or BLS 4.0
  food. The 60 linked foods (60 of the 80 rations, all 62 measures) were re-pointed one by one to
  the same food in the same state; the list is `product/spikes/FD-033-C-rekey-mapping.md`. No figure,
  food name, measure or page reference from the article changed.

This licence covers these data files only. It does not extend to the fdiet source code.
