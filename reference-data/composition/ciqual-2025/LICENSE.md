# CIQUAL 2025 — licence and attribution

The files in this folder are the French food composition table published by ANSES, copied
**unmodified** from the Zenodo record (byte-identical; SHA-256 in `../manifest.csv`).

- Title: Ciqual French food composition table 2025 (data dated 2025-11-03, published 2025-11-19)
- Publisher: Agence nationale de sécurité sanitaire de l'alimentation, de l'environnement et du
  travail (ANSES), Ciqual team — <https://ciqual.anses.fr/>, contact ciqual@anses.fr
- Copy taken: Zenodo record <https://zenodo.org/records/17550133>, DOI `10.5281/zenodo.17550133`
  (concept DOI `10.5281/zenodo.17550132`), retrieved 2026-10-03
- Licence: **Creative Commons Attribution 4.0 International (CC BY 4.0)**,
  <https://creativecommons.org/licenses/by/4.0/>, as the Zenodo record states. (The copy on
  Recherche Data Gouv, doi:10.57745/RDMHWY, is also offered under Etalab Licence Ouverte 2.0; the
  Zenodo copy is the one taken here.)

## Attribution to show wherever these figures are shown

> ANSES. Ciqual French food composition table 2025. https://ciqual.anses.fr/ —
> doi:10.5281/zenodo.17550133. CC BY 4.0.

## Changes made by fdiet

None to these files. fdiet reads them into `composition_foods`, where a qualified value
(`traces`, `< 0,2`, `-`) is stored as a blank rather than a number, energy is the kcal column of
Regulation (EU) 1169/2011 and protein is the Jones-factor column. Spanish names are fdiet's own
work and live apart, in `../composition-es/` (CC BY 4.0).
