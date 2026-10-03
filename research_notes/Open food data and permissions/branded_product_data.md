# Branded / processed food product data for fdiet: provenance and licences

Research date: 2026-10-02. All URLs were fetched on that date unless marked otherwise.

## Q1. Where does fooddata.csv come from, and under what terms?

### Takeaway
`fooddata.csv` is an extract of AESAN's open-data file **"Datos de composición de alimentos y bebidas comercializados en España en 2022"** (`BasedatosWeb.xlsx`, 29,575 products, label data collected by Kantar Worldpanel). I confirmed this by downloading the file and comparing it with the CSV. AESAN's reuse terms are the general Spanish public-sector conditions: commercial and non-commercial reuse is allowed if you cite the source, do not distort the content and give the date of the last update. The page names no CC licence.

### Cited Findings
- **Exact download page:** AESAN Datos abiertos → "Alimentos y Bebidas": https://www.aesan.gob.es/en/datos-abiertos/alimentos-y-bebidas. The dataset is "Datos de composición de alimentos y bebidas comercializados en España en 2022". It is an Excel file at `https://www.aesan.gob.es/en/dam/jcr:5f9691b8-10dc-4c23-9963-9b4e829308eb/BasedatosWeb.xlsx`, and the accompanying report is at `https://www.aesan.gob.es/en/dam/jcr:c0653dfc-cf6d-44e9-a0ac-c29e0897f64a/INFORME_SOBRE_LA_BASE_DE_DATOS.pdf`. The page describes the data as "collected by third parties during 2022" — [AESAN Alimentos y Bebidas](https://www.aesan.gob.es/en/datos-abiertos/alimentos-y-bebidas)
- **First-hand verification (done during this research):** I downloaded `BasedatosWeb.xlsx` (12.2 MB) and inspected it.
  - Sheet `DATOS` spans `A1:AE29576`, which is 31 columns and 29,575 product rows. A hidden sheet is called `Tabla1`.
  - Header: `IdCategoria, Categoria, IdSubcategoria, Subcategoria, AÑO, FUENTE, CuotaMercadoTotalEan, EAN, Nombrecomercial, Fabricante, Marca, Submarca, DenominacionLegal, Ingredientes, Tamañoporción, EnergiaKJ, EnergiaKC, Grasas, GrasasSat, Carbohidratos, Azúcares, Proteínas, Sal, Sodio, GrasasMSat, GrasasPSat, Almidon, Fibra, PoliAlcoh, Edulcorantes, Índice`.
  - The `FUENTE` values are "Kantar Worldpanel", plus one misspelt "Kantar Wordlpanel".
  - The string `BEKIND BARRA CEREAL CARAMELO ALMENDRA Y SAL` is present, as is the category "Chocolate y productos de confitería, coberturas dulces y postres".
  - fdiet's CSV writes that category with ";" instead of ",", so commas inside fields appear to have been replaced when the CSV was made. That is an inference.
  - Source: [BasedatosWeb.xlsx](https://www.aesan.gob.es/en/dam/jcr:5f9691b8-10dc-4c23-9963-9b4e829308eb/BasedatosWeb.xlsx)
- The xlsx columns missing from fdiet's 19-column CSV match exactly the `food_items` columns that CLAUDE.md says "stay null": `CuotaMercadoTotalEan` (market share), `Fabricante`, `Submarca`, `Tamañoporción`, `Sodio`, `Fibra`, and others. This suggests the schema was designed from the full AESAN file — [BasedatosWeb.xlsx](https://www.aesan.gob.es/en/dam/jcr:5f9691b8-10dc-4c23-9963-9b4e829308eb/BasedatosWeb.xlsx)
- **Size correction:** the local `fooddata.csv` has 29,966 physical lines (13.7 MB). Quoted ingredient lists span several lines, which explains why that is more than 29,575. **CLAUDE.md's "~100 k rows" is wrong; the real figure is ~29.6 k products.** Measured locally. The file was first committed in 4b0ec49 (2026-08-25).
- Scope: "29,575 food and beverage products … representing 85.7% of total food market share from November 2021 to November 2022". The database supports the "Plan de colaboración para la mejora de la composición de los alimentos y bebidas" and the monitoring of reformulation agreements — [AESAN Composición de alimentos y bebidas](https://www.aesan.gob.es/nutricion/composicion-reformulacion-mejora/composicion-alimentos-bebidas); [AESAN Estudios de composición](https://www.aesan.gob.es/nutricion/composicion-reformulacion-mejora/estudio-composicion-alimentos)
- The report is also listed as "Informe sobre la base de datos de alimentos y bebidas comercializados en España en 2022" — [DSCA](https://www.dsca.gob.es/es/publicacion/informe-base-datos-alimentos-bebidas-comercializados-espana-2022). It is used in peer-reviewed work, e.g. *Rev Esp Salud Pública* e202609059 on biscuits and pastries — [OJS Sanidad](https://ojs.sanidad.gob.es/index.php/resp/article/view/1600)
- **Disclaimers on the dataset page:** "La AESAN declina toda responsabilidad por cualquier daño directo o indirecto… causado por cualquier error en el conjunto de datos". "Los datos que se suministran… pueden haber sufrido modificaciones con posterioridad". The economic operator remains responsible for the accuracy of the label — [AESAN Alimentos y Bebidas](https://www.aesan.gob.es/en/datos-abiertos/alimentos-y-bebidas)
- **Reuse terms (AESAN Aviso legal):** the information is reusable for commercial and non-commercial purposes under these conditions:
  - "El usuario queda obligado a citar la fuente de los documentos objeto de la reutilización"
  - "Queda prohibida en cualquier circunstancia la desnaturalización del contenido"
  - "El usuario queda obligado a mencionar la fecha de la última actualización"
  - Source: [AESAN Aviso legal](https://www.aesan.gob.es/en/aviso-legal)

### Inferences
- These are the standard conditions of Spain's public-sector-information reuse regime (Ley 37/2007 and RD 1495/2011 general conditions). They are compatible with an open-source repo and with commercial use, which makes them **far less restrictive than BEDCA's non-commercial terms**. The fetched aviso legal page did not cite the law by number (unverified link).
- What fdiet would need to do to comply:
  - Attribute the file to "AESAN – Datos de composición de alimentos y bebidas comercializados en España en 2022 (datos recogidos por Kantar Worldpanel)" with the URL.
  - State the date of last update.
  - Say that commas in categories were changed to ";" and that columns were dropped. This avoids any claim of "desnaturalización"; reformatting is not a change of meaning, but documenting it is prudent.
  - Add a `FOODDATA-ATTRIBUTION.txt` parallel to `BEDCA-ATTRIBUTION.txt`.
- Kantar Worldpanel collected the label data under contract. AESAN's open publication suggests AESAN holds or cleared the reuse rights, but Kantar's contract terms are not public (unverified).

### Gaps
- No explicit CC BY 4.0 or other named licence was found on the dataset page. datos.gob.es did not show a federated entry for this dataset in search, so the licence field there is unverified.
- The AESAN report PDF exceeded 10 MB and could not be fetched. Its methodology text (how Kantar collected labels; whether Kantar retains rights) is unverified.
- The page publishes no update date for the file. AESAN's July 2025 open-data press release (`Nota_Datos_abiertos.htm`) now returns 404.

## Q2. Open Food Facts: licence, share-alike, exports, Spain coverage, API terms

### Takeaway
Open Food Facts licenses its database under ODbL 1.0, its individual contents under DbCL 1.0 and its images under CC BY-SA 3.0. These obligations would apply to fdiet:
- **Storing an OFF extract as CSV in the repo is a Derivative Database.** It must stay ODbL and be attributed.
- **An app screen showing figures is a Produced Work.** It only needs a notice, unless it comes from a *derivative* database that is publicly used, in which case that derivative database must be offered as well.
- **Merging OFF rows into `food_items` would make that table a Derivative Database.** Keeping OFF in its own unmodified table would make it a Collective Database.

### Cited Findings
- "The Open Food Facts database is available under the Open Database License. Individual contents of the database are available under the Database Contents License. Products images are available under the Creative Commons Attribution ShareAlike licence" (CC BY-SA 3.0) — [OFF Terms of use](https://world.openfoodfacts.org/terms-of-use)
- Attribution: re-users must "mention the licence and to attribute the authorship to Open Food Facts with a link to https://openfoodfacts.org" or to the product page. "Derivative works must be shared under the same conditions." Third-party rights such as "copyright for the product design… trademark rights" may still apply — [OFF Terms of use](https://world.openfoodfacts.org/terms-of-use)
- **ODbL 1.0 definitions:**
  - *Derivative Database*: "a database based upon the Database, and includes any translation, adaptation, arrangement, modification, or any other alteration of the Database or of a Substantial part of the Contents".
  - *Produced Work*: "a work (such as an image, audiovisual material, text, or sounds) resulting from using the whole or a Substantial part of the Contents (via a search or other query)…".
  - *Collective Database*: "this Database in unmodified form as part of a collection of independent databases… will not be considered a Derivative Database".
  - Source: [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/)
- **ODbL obligations by section:**
  - §4.4: a publicly used Derivative Database must be under ODbL or a compatible licence.
  - §4.5: Collective Databases and Produced Works do not trigger share-alike, and internal use is exempt.
  - §4.6: publicly using a Derivative Database, or a Produced Work from one, requires offering the derivative database or an alteration file in machine-readable form.
  - §4.3: a Produced Work used publicly needs a notice naming the database and the ODbL.
  - Source: [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/). These are paraphrases from the fetch summary; check the §4.6 wording against the original.
- **Bulk exports:**
  - CSV (food): `https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz`, about 0.9 GB compressed and 9 GB uncompressed.
  - JSONL: `https://static.openfoodfacts.org/data/openfoodfacts-products.jsonl.gz`.
  - Parquet on Hugging Face: `https://huggingface.co/datasets/openfoodfacts/product-database/resolve/main/food.parquet?download=true`.
  - MongoDB dump: `https://static.openfoodfacts.org/data/openfoodfacts-mongodbdump.gz`.
  - Daily deltas over a 14-day window: `https://static.openfoodfacts.org/data/delta/index.txt`.
  - RDF export.
  - Source: [OFF Data](https://world.openfoodfacts.org/data)
- **API terms:**
  - Production use is welcome "as long as 1 API call = 1 real scan by a user. Any attempt to scrape the database using the API will very likely be blocked" — [OFF Data](https://world.openfoodfacts.org/data)
  - Rate limits: "15 req/min/IP address for all read product queries" and "10 req/min/IP address for all search queries". Search-as-you-type is not allowed. A User-Agent of the form `AppName/Version (ContactEmail)` is required. "If you need to fetch more than a few hundred products… download the data as a CSV or JSONL file" — [OFF API docs](https://openfoodfacts.github.io/openfoodfacts-server/api/)
  - Flag: older OFF documentation said 100 req/min for product reads. The 15/min figure is what the current page returned.
- **Spain coverage:** es.openfoodfacts.org showed "372.089" products — [OFF España](https://es.openfoodfacts.org/). This is crowd-sourced, of uneven completeness and includes non-Spanish products sold in Spain.

### Inferences
- **Repo-stored CSV extract.** A filtered OFF subset with renamed columns or dropped products is a Derivative Database, so the CSV itself must be ODbL with attribution. This sits fine beside fdiet's code licence, because ODbL covers the data file and not the code.
- **The app combining it with BEDCA/AESAN data.** If OFF rows sit unmodified in their own table, the result is a Collective Database, and share-alike applies to the OFF part only. If they are merged or deduplicated into `food_items` by EAN with AESAN rows, that table becomes a Derivative Database. If it is publicly used, it must be offered under ODbL. That is acceptable for the AESAN portion (permissive reuse), but **not for BEDCA**, which may not be modified and is non-commercial. Keep BEDCA separate.
- **A diet's nutrition totals shown to a patient** are Produced Works. They need a non-intrusive OFF notice in the footer, like the existing BEDCA line.
- **Data quality fields** exist in OFF exports (`completeness`, `states_tags`, `data_quality_errors_tags` / `warnings_tags`, `nutrition_data_per`). I did not verify them in this session; they come from training knowledge and should be checked against the export schema.

### Gaps
- I did not obtain the official OFF wiki page on ODbL guidance ("ODBL_License"); it was blocked by a bot filter.
- The current CSV row count for Spain-specific products (countries_tags = en:spain) was not retrieved.

## Q3. Other branded datasets usable in Spain

### Takeaway
USDA Branded Foods is CC0 but almost entirely US products. GS1 Spain/GDSN data is a member-only commercial exchange and not open data. TABULA (CEU) is non-commercial, with no bulk download. For Spanish branded products, the realistic open options are **AESAN 2022 (already in the repo) plus Open Food Facts**.

### Cited Findings
- **USDA FoodData Central:**
  - "USDA FoodData Central data are in the public domain and they are not copyrighted. They are published under CC0 1.0 Universal"; "No permission is needed… we request that users list FoodData Central as the source". The API is limited to 1,000 requests per hour per IP — [FDC API guide](https://fdc.nal.usda.gov/api-guide/)
  - The Branded Foods download, release of December 2025, is 427 MB zipped as CSV (2.9 GB unzipped) — [FDC downloads](https://fdc.nal.usda.gov/download-datasets/)
  - GBFPD is a public-private partnership between USDA, IAFNS, GS1 US, 1WorldSync, Label Insight and UMD. Data comes in via Label Insight or 1WorldSync/GDSN, and the database has over 368,000 products — [GBFPD documentation](https://fdc.nal.usda.gov/docs/GBFPD_Documentation_and_Download_User_Guide_Jan2024.pdf); [ScienceDirect](https://www.sciencedirect.com/science/article/pii/S0889157521004506)
- **GS1 Spain (AECOC):**
  - GDSN is a network for "secure and continuous exchange of product data between manufacturers and distributors" through AECOC DATA — [GS1 Spain GDSN](https://www.gs1es.org/compartir-estandares-gs1/gdsn/)
  - Verified by GS1 exposes only basic identity data (GTIN, brand, description) — [Verified by GS1](https://www.gs1es.org/verified-by-gs1/)
  - I found no open licence for either.
- **TABULA®:**
  - Run by IuAyS-CEU. Version 3.0 (September 2025) has "más de 10.000 alimentos y bebidas"; access is through a search page only, under "Todos los derechos reservados" — [TABULA Presentación](https://ias.ceu.es/tabula-bbdd/presentacion/)
  - Terms, as quoted by the search snippet: the data "cannot be reproduced without clear indication of the original source and cannot be modified… Reproduction, translation or any use that is not personal, educational or non-commercial of data in electronic format is subject to express authorization" — [search summary of ias.ceu.es](https://ias.ceu.es/tabula-bbdd/buscador-alimentos/); [Tabula resumen PDF](https://ias.ceu.es/wp-content/uploads/Tabula_Resumen_ESP.pdf)
  - Flag: this terms text came through a search summary, not a direct fetch.

### Inferences
- USDA branded data is legally ideal but practically useless for Spanish EANs, apart from a few multinational products.
- TABULA's terms mirror BEDCA's: non-commercial and no modification. Do not import it without written permission.

### Gaps
- No EU-level open branded dataset was identified. EFSA and the JRC publish no open EAN-level product database. I did not research this exhaustively, so treat it as unverified.
- The exact GS1 Spain terms of service for data recipients were not retrieved.

## Q4. Legal aspects of storing product names, brands, ingredients and nutrition declarations

### Takeaway
The individual facts (nutrient values, EANs) are not copyrightable. Brand names are trademarks, and using them to identify the product is normally fine. Ingredient lists are mandatory label text with minimal originality. The real legal hook is the **EU sui generis database right** in the compiled collection, and the publisher's licence (AESAN, OFF) is what clears it.

### Cited Findings
- **Directive 96/9/EC, art. 7(1):** the database maker gets a sui generis right where there has been "qualitatively and/or quantitatively a substantial investment in either the obtaining, verification or presentation of the contents". Extracting or re-utilising a substantial part infringes it — [EUR-Lex C-203/02](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=celex%3A62002CJ0203)
- **CJEU, BHB v William Hill (C-203/02):** investment in "obtaining" means resources used "to seek out existing independent materials and collect them", not to create the data — [EUR-Lex C-203/02](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=celex%3A62002CJ0203); [SCL commentary](https://www.scl.org/781-court-of-appeal-cuts-back-the-legal-protection-for-databases/)
- **OFF's own terms** warn that its licences cover only OFF's rights and that "copyright for the product design… trademark rights" of third parties may still apply — [OFF Terms of use](https://world.openfoodfacts.org/terms-of-use)

### Inferences (not legal advice)
- **Database right.** Kantar's label-collection work for AESAN is exactly the "obtaining/verification" investment the database right protects. Extracting all 29.6k rows is a substantial extraction, and is lawful **only because AESAN offers reuse**. That makes keeping the attribution and the update date essential.
- **Trademarks.** Brand names such as "BEKIND" in a data column serve to identify the product (descriptive or referential use). This is generally permitted under EU trademark law (Reg. 2017/1001 art. 14, limitation for indicating goods; not fetched, unverified). Avoid logos and anything implying endorsement.
- **Label text.** Ingredient lists and nutrition declarations are mandated by Reg. (EU) 1169/2011, and their wording is dictated by regulation, so copyright originality is unlikely. Pack images and designs *are* copyrighted, which is why OFF images are separately CC BY-SA and carry third-party caveats. Do not store images.
- **Accuracy and liability.** Both AESAN and OFF disclaim accuracy. The 2022 label data may be stale after reformulation, so the UI should show "datos de etiqueta 2022" as context.
- **Personal data.** The datasets contain none (product-level only).

### Gaps
- I fetched no Spanish case law or AEPD/OEPM guidance on reusing trademarks in open food datasets.
- The text of Spain's LPI art. 133 (transposition of the sui generis right) and of Ley 37/2007 art. 8 was not fetched.
