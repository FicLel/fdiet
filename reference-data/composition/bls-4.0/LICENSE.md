# BLS 4.0 — licence and attribution

The files in this folder are the German nutrient database (Bundeslebensmittelschlüssel)
published by the Max Rubner-Institut, extracted **unmodified** from `BLS_4_0_2025_DE.zip`
(byte-identical; SHA-256 of the zip and of each file in `../manifest.csv`).

- Title: Bundeslebensmittelschlüssel (BLS), Version 4.0 — Deutsche Nährstoffdatenbank
  (Stand: Dezember 2025)
- Publisher: Max Rubner-Institut (MRI), Karlsruhe — <https://blsdb.de/>
- Copy taken: <https://blsdb.de/download>, retrieved 2026-10-03. The download link carries a
  token the page issues and rotates, so the page and the DOI are recorded, not the link.
- DOI: `10.25826/Data20251217-134202-0`
- Licence: **Creative Commons Attribution 4.0 International (CC BY 4.0)**,
  <https://creativecommons.org/licenses/by/4.0/>. The download page says (verbatim): "Die Daten
  des Bundeslebensmittelschlüssels (BLS) stehen als Open Data zur freien Verfügung. Die Nutzung
  ist unter der Lizenz CC BY 4.0 (Creative Commons Namensnennung 4.0 International) gestattet.
  Bei Verwendung ist das Max Rubner-Institut als Herausgeber zu nennen."

## Attribution to show wherever these figures are shown

> Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0 — Deutsche
> Nährstoffdatenbank. Karlsruhe. DOI: 10.25826/Data20251217-134202-0. CC BY 4.0.

## Changes made by fdiet

None to these files. fdiet reads the data sheet into `composition_foods`, where a qualified value
(`TR`, `<LOD`, `<LOQ`, `-`) is stored as a blank rather than a number. Spanish names are fdiet's
own work and live apart, in `../composition-es/` (CC BY 4.0).
