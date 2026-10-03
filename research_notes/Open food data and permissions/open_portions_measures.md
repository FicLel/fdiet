# Open datasets for portion sizes, household measures, unit weights, densities and yields (fdiet replacement for permission-gated Spanish sources)

Research date: 2026-10-02. Tool budget was limited (about 18 calls), so several licences below are flagged **UNVERIFIED**: they come from background knowledge and were not confirmed on a primary page during this session.

## 1. Which international datasets are openly reusable, and what do they hold?

### Takeaway
The USDA datasets (FoodData Central SR Legacy / FNDDS / Foundation portion tables, the 2014 cooking yields and Retention Factors Release 6) are the only large, clearly reusable sources of household-measure weights and yields. The licence is US public domain / CC0, and commercial use and redistribution are both allowed. Their measures are US units (cup, tbsp, "1 medium"), so mapping them to Spanish measures takes curation. The FAO/INFOODS Density Database is free to download, but FAO's terms allow non-commercial use only, on request. UK, French and EFSA sources are open-licensed, but they hold either no household-measure tables (CIQUAL) or not-verified portion datasets.

### Cited Findings
**USDA FoodData Central (SR Legacy, FNDDS, Foundation Foods)**
- Download page lists Foundation Foods (latest Dec 2025, JSON/CSV, 3.4 MB CSV zip), SR Legacy (April 2018, final, "will not be updated", 6.7 MB CSV zip) and FNDDS 2021-2023 (Oct 2024, JSON/CSV, 200 MB CSV zip) — [FDC Download Datasets](https://fdc.nal.usda.gov/download-datasets/)
- That download page itself carries no licence statement — [FDC Download Datasets](https://fdc.nal.usda.gov/download-datasets/). **UNVERIFIED in this session:** the FDC FAQ / data.gov listing says the data are public domain under CC0 1.0 ("USDA FoodData Central data are in the public domain and not copyrighted…; published under CC0 1.0"). Confirm at https://fdc.nal.usda.gov/faq before committing derived CSVs.
- FNDDS contains food codes, descriptions, **portion weights** and nutrient profiles. The 2021-2023 release has ~22,000 weights for portions of foods and beverages, with a separate Excel file "Portions and Weights" — [FNDDS 2021-2023 fact sheet](https://www.ars.usda.gov/ARSUserFiles/80400530/pdf/fndds/FNDDS_2021_2023_factsheet.pdf); [FNDDS 2021-2023 documentation](https://www.ars.usda.gov/ARSUserFiles/80400530/pdf/fndds/2021_2023_FNDDS_Doc.pdf); [FNDDS home, USDA ARS](https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/food-surveys-research-group/docs/fndds/)
- Structure (background knowledge, **UNVERIFIED** this session): the FDC CSV export has `food_portion.csv` (fdc_id, amount, measure_unit_id, portion_description, modifier, gram_weight) plus `measure_unit.csv` (cup, tbsp, tsp, slice, piece, "medium", etc.). SR Legacy has ~7,800 foods and ~15,000 portion rows. Weights are as-consumed (edible) portions. SR Legacy also publishes a "refuse" percentage per food, which gives an edible-portion factor.

**USDA cooking yields and retention factors**
- USDA Table of Nutrient Retention Factors Release 6 (2007): retention factors for 16 vitamins, 8 minerals and alcohol for about 290 foods; widely used by public and private databases. PDF: https://www.ars.usda.gov/ARSUserFiles/80400530/pdf/retn06.pdf — [Nutrient retention factors, USDA ARS](https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/nutrient-retention-factors/); also on [Ag Data Commons](https://agdatacommons.nal.usda.gov/articles/dataset/USDA_Table_of_Nutrient_Retention_Factors_Release_6_2007_/24660888) (the page returned 403, so the licence was **not verified**; Ag Data Commons items are usually CC0 1.0).
- The Table of Cooking Yields for Meat and Poultry (2014) is already partly used by fdiet as US public domain (per the project's CLAUDE.md). It was not re-fetched this session.
- As works of the US federal government, both are not subject to US copyright (17 U.S.C. §105). This is background knowledge and holds in the US. Outside the US, USDA states CC0 for FDC (**UNVERIFIED**, see above).

**FAO/INFOODS Density Database v2.0 (2012)**
- PDF: https://www.fao.org/fileadmin/templates/food_composition/documents/density_DB_v2_0_01.pdf; catalogue entry: [FAO](https://www.fao.org/food-composition/tables-and-databases/detail/(global--2012)-fao-infoods-density-database---version-2/en); Excel download from the [INFOODS databases page](https://www.fao.org/infoods/infoods/tables-and-databases/faoinfoods-databases/en/)
- Licence (search summary of the FAO notice): non-commercial uses authorised free of charge **upon request**; commercial reproduction may incur fees; contact copyright@fao.org — [FAO density DB v2 PDF](https://www.fao.org/fileadmin/templates/food_composition/documents/density_DB_v2_0_01.pdf). It is **not** an open licence. (Newer FAO publications are often CC BY-NC-SA 3.0 IGO. Whether the density DB has been relicensed was not checked.)
- FAO/INFOODS Guidelines for Food Matching v1.2 explain how yield and retention factors are used and recommend factors based on local cooking methods. They are guidance, not a dataset — [FAO/INFOODS Food Matching v1.2](https://www.fao.org/fileadmin/templates/food_composition/documents/upload/INFOODSGuidelinesforFoodMatching_version_1_2.pdf)

**EFSA**
- EFSA Supporting Publications is open access, and "all authors must use the CC BY Creative Commons License" — [EFSA Supporting Publications open access](https://efsa.onlinelibrary.wiley.com/journal/23978325/homepage/open-access). EU Menu external scientific reports (the country survey reports) are published there and in EFSA Journal — [EU Menu reports TOC](https://efsa.onlinelibrary.wiley.com/doi/toc/10.1002/(ISSN)1831-4732.scientificreports)
- The EU Menu Guidance appendix 5.2.2 gives **maximum** portions per eating occasion and per day, used to flag outliers. These are outlier caps, not typical household measures — [EFSA EU Menu Guidance App. 5.2.2](https://www.efsa.europa.eu/sites/default/files/efsa_rep/blobserver_assets/3944A-5-2-2.pdf)
- **Not verified:** the licence and availability of the PANCAKE / EU Menu picture book (photo series of portion sizes) as a reusable table. It is probably an EFSA supporting publication (and so CC BY 4.0), but its contents are photographs with gram weights, not Spanish household measures.

**UK FSA**
- FSA material is reusable free of charge under the Open Government Licence v3.0 "except where otherwise stated" — [FSA terms and conditions](https://www.food.gov.uk/terms-and-conditions)
- **Not verified:** whether FSA *Food Portion Sizes* 3rd ed. (2002, The Stationery Office book) falls under that OGL. It was a TSO priced publication under Crown copyright, and no OGL dataset release was found. Treat it as **unconfirmed**.
- Crown-copyright school food standards portion guidance exists (OGL on GOV.UK) — [School food standards portion sizes](https://www.gov.uk/government/publications/school-food-standards-practical-guide-and-resources-for-schools/school-food-standards-portion-sizes-and-food-groups-for-secondary-schools)

**CIQUAL (Anses, France)**
- Ciqual 2020 is under the Etalab Licence Ouverte (commercial use allowed, attribution, no share-alike), in XML/XLS. It has been superseded by a version on research.data.gouv — [data.gouv.fr Ciqual 2020](https://www.data.gouv.fr/datasets/table-de-composition-nutritionnelle-des-aliments-ciqual-2020); [Licence Ouverte 2.0](https://www.data.gouv.fr/datasets/etalab-licence-ouverte-v2-0-2)
- No portion or household-measure table in Ciqual was identified. It is composition only.

### Inferences
- For fdiet, USDA FNDDS/SR Legacy `food_portion` is the best bulk source of unit weights by size ("1 medium apple", "1 slice bread", "1 tbsp olive oil"). It suits `ref_food_measures` rows that carry a source and a point weight, which fits fdiet's model. Each row must still be mapped by hand from a USDA food to a BEDCA food and from a US measure to a Spanish measure (cup = 240 ml, not a Spanish *taza*; the tbsp is ~14.8 ml where Spain uses 10–15 ml).
- SR Legacy refuse percentages could fill edible-portion factors where BEDCA's `edible_portion` is missing. They are factual values from a public-domain source.
- Densities: USDA cup/tbsp gram weights imply densities (g per ml) and are a public-domain substitute for the FAO density DB.

### Gaps
- Exact CC0 wording on the FDC FAQ and Ag Data Commons pages was not retrieved (403/no statement).
- The licence of the FSA Food Portion Sizes book, and any newer UK portion dataset (e.g. Intake24 portion data, which I believe is open-source, **unverified**).
- Nordic portion data (Fineli in Finland is CC BY 4.0 and has unit weights by size; Norway's "Mål, vekt og porsjonsstørrelser"): **not checked this session**.
- The EFSA PANCAKE picture book's licence and data format.

## 2. Spanish / Latin American open sources

### Takeaway
Two Spanish nutrition journals publish openly, under different licences. RENHyD shows **CC BY-SA 4.0**, which is compatible with fdiet's share-alike handling of the 5 al día data. Nutrición Hospitalaria is **CC BY-NC-SA 4.0**, which is non-commercial and would add an NC restriction to fdiet's reference data. The UCM tables (Aparicio & Perea 2015) carry no open licence: they are an appendix of a commercial Panamericana book. No Spanish public health service carbohydrate ration table with a Creative Commons notice was found.

### Cited Findings
- RENHyD "about" page: articles are under "Licencia de Creative Commons Reconocimiento-Compartir Igual 4.0 Internacional" (CC BY-SA 4.0) — [RENHyD about](https://www.renhyd.org/renhyd/about). One search summary claimed CC BY-NC-SA, which contradicts this; the primary page says BY-SA. Older articles may carry different notices, so check each article's PDF footer. The journal is indexed in [DOAJ](https://doaj.org/toc/2174-5145) and [SciELO](https://scielo.isciii.es/scielo.php?script=sci_serial&pid=2174-5145).
- Nutrición Hospitalaria (official journal of SENPE/SEÑ) publishes under CC BY-NC-SA 4.0 — [Nutrición Hospitalaria](https://www.nutricionhospitalaria.org/) (search summary; not fetched).
- UCM "Tablas de raciones de alimentos y medidas caseras": Aparicio A, Perea JM (2015), Appendix XII of *Nutriguía. Manual de Nutrición Clínica* 2nd ed., Editorial Médica Panamericana. No licence is stated, and the authors call the values "orientativos" — [UCM page](https://www.ucm.es/idinutricion/tablas-de-raciones-de-alimentos-y-medidas-caseras); [PDF](https://www.ucm.es/data/cont/docs/458-2015-10-22-pesos-medidas-caseras-raciones-2015.pdf). → **all rights reserved; do not extract in bulk**.
- Peru, CENAN/INS "Tablas Auxiliares para la Formulación y Evaluación de Regímenes Alimentarios" (TAFERA 2016) is reported to contain 1,027 entries of household measures with gross and net weight and % edible portion — [gob.pe PDF](https://cdn.www.gob.pe/uploads/document/file/1427367/TAFERA%202016%20VF.pdf.pdf). **Licence not verified.** Peruvian government publication. Strong content match (Spanish wording, gross/net), but Peruvian foods and measures.
- Argentina, Ministerio de Agroindustria booklet on household measures and nutrition labelling — [alimentosargentinos PDF](https://alimentosargentinos.magyp.gob.ar/HomeAlimentos/Publicaciones/pdf/info_nutricional_1.pdf) (licence not checked).
- Carbohydrate ration tables found are all from hospitals or associations with no open licence seen: Hospital Sant Joan de Déu ([page](https://diabetes.sjdhospitalbarcelona.org/es/diabetes-tipo-1/raciones-hidratos-carbono/1000); fetch failed on a TLS error), Fundación para la Diabetes ([materials](https://www.fundacionparalasalud.org/general/materiales/b/7)), the Serafín Murillo table via diabetesalicante ([PDF](https://www.diabetesalicante.org/wp-content/uploads/2025/05/gallery-tabla-hidratos-de_carbono_serafin-murillo.pdf)) and Fadex ([PDF](https://www.fadex.org/bddocumentos/3/TABLAHC.pdf)). None from Osakidetza, SAS, SERMAS or Generalitat with a CC notice turned up.
- 5 al día 2019 (CC BY-SA 4.0) is already used by fdiet (project CLAUDE.md). It was not re-verified.

### Inferences
- RENHyD articles under CC BY-SA 4.0 can be mined for tables. Derived CSVs must stay CC BY-SA and be attributed, the same regime as the existing 5 al día folder. Nutrición Hospitalaria tables would make the derived file NC, which conflicts with any future commercial use.
- Even where a whole table cannot be copied, individual factual values can be cross-checked against them and re-entered as fdiet's own criteria with citation (see section 3).

### Gaps
- Mexico's Sistema Mexicano de Alimentos Equivalentes (SMAE, 4th/5th ed., Fomento de Nutrición y Salud A.C.) is, to my knowledge, a commercially sold book, all rights reserved (**UNVERIFIED**).
- AESAN ENIDE (2011) / ENALIA (2012–2014) reports: their reuse notices and whether they publish portion weights were **not checked**. The AESAN aviso legal and the Spanish public-sector reuse law (Ley 37/2007, RISP) would allow reuse with attribution unless a notice says otherwise. Verify the specific AESAN aviso legal.
- Spanish university theses (UCM eprints, RIUMA, etc.) often carry CC BY-NC-ND. Not searched.
- No specific RENHyD article publishing a household-measure table was identified. Search candidates ("pesos medidas caseras" RENHyD; the RENHyD v23n4 article was returned by a search — [SciELO PDF](https://scielo.isciii.es/pdf/renhyd/v23n4/2174-5145-renhyd-23-04-205.pdf) — but its content was not checked).

## 3. Legal point: individual factual values vs. extracting a substantial part of a database (Spain/EU)

### Takeaway
Spanish law (TRLPI arts. 133–137, transposing Directive 96/9/EC) protects the *investment* in a database against extraction or reuse of all or a **substantial part** of its contents. It also forbids repeated, systematic extraction of insubstantial parts that harms normal exploitation. A single fact such as "1 cucharada sopera de aceite = 10 ml" is not protected by copyright, and taking a handful of such values, cited and cross-checked, is generally lawful. Bulk transcription of a SENC/DIAL table, or step-by-step copying of it, is not.

### Cited Findings
- Art. 133 TRLPI: the sui generis right protects the substantial investment (financial, time, effort) in obtaining, verifying or presenting the contents. *Extracción* is the permanent or temporary transfer of all or a substantial part to another medium. *Reutilización* is making all or a substantial part available to the public. Repeated, systematic extraction of insubstantial parts contrary to normal exploitation is not authorised — [Iberley art. 133](https://www.iberley.es/legislacion/articulo-133-ley-propiedad-intelectual); [Cerlalc, Título VIII](https://cerlalc.org/laws_rules/real-decreto-legislativo-no-1-de-1996-por-el-que-se-aprueba-el-texto-refundido-de-la-ley-de-propiedad-intelectual-regularizando-aclarando-y-armonizando-las-disposiciones-vigentes-sobre-la-materia/titulo-viii-derecho-sobre-las-bases-de-datos/); [consolidated TRLPI, Ministerio de Cultura](https://www.cultura.gob.es/en/dam/jcr:13e69add-7119-41c5-aeaa-e0d289316ef9/trlpropiedad-intelectual.pdf)
- Introduced into Spanish law by Ley 5/1998 transposing Directive 96/9/CE — [BOE-A-1998-5568](https://boe.es/diario_boe/txt.php?id=BOE-A-1998-5568)
- Explanations of what the sui generis right protects — [OCW Unizar](https://ocw.unizar.es/ocw/mod/page/view.php?id=2175); [Cysae](https://cysae.com/aproximacion-al-derecho-sui-generis-sobre-las-bases-de-datos/)
- Background knowledge, **not fetched this session**:
  - CJEU *British Horseracing Board v William Hill* (C-203/02, 2004) holds that investment in *creating* data, as opposed to obtaining or verifying existing data, does not count. Measured portion weights are arguably "obtained/verified", so a measured table is likely protected.
  - Facts and ideas are not copyrightable. Copyright in a database (art. 12 TRLPI) covers only the original selection or arrangement.
  - Lawful users may extract insubstantial parts (art. 134).
  - Short quotation for teaching or research (art. 32) is narrow.
  - Licence or website terms can add contractual restrictions.
  - Not legal advice; a lawyer should confirm.

### Inferences
- In practice for fdiet: (a) bulk sources must come from openly licensed or public-domain sets (USDA, CC BY/BY-SA articles, OGL, Etalab). (b) A few individual measures may be entered as the **nutritionist's own criteria** (fdiet's `global_criterion` rows), labelled as hers and optionally citing where she cross-checked them. They must not be labelled as a SENC/DIAL dataset, and must not be added systematically until they reconstruct the table. (c) The photographs and layout of the FINUT guide are copyright works independently of the numbers.
- Physical constants and conventions such as "1 cucharada sopera ≈ 10–15 ml" appear in many independent sources (USDA, AESAN 2022 which fdiet already uses). A value corroborated by an open source should be attributed to the open source.

### Gaps
- No Spanish court decision applying arts. 133–134 to nutrition or food tables was found.
- What counts as "substantial" for a few-hundred-row measures table is unresolved in case law. Qualitative substantiality (the most valuable rows) could matter even for small takes.
