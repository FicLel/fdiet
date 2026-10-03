# Spanish nutrition sources that need permission — licence status and request path (as of 2026-10-02)

Scope: the sources fdiet (open-source, non-commercial, Spanish diet planner) has *not* loaded because permission is
needed. Research notes, not legal advice. Items marked **(unverified)** were not confirmed against a primary page in
this session. Wayback Machine could not be queried from this environment (web.archive.org fetch blocked), so no
archive URLs were checked.

## Legal frame (Spain/EU) used to read every source below

### Takeaway
Bare numbers (a ration weight, a gram value) are facts, but a *table* of them is protected twice in Spain: as a
collection (copyright, LPI art. 12) when selection/arrangement is original, and by the sui generis database right
(LPI arts. 133–137) when obtaining/verifying it took substantial investment. Loading a whole annex or table into a
repo CSV is a substantial extraction and needs permission unless a licence or the public-sector reuse regime allows it.

### Cited Findings
- Spain's public-sector reuse regime (Ley 37/2007 and RD 1495/2011) underpins the "condiciones generales de
  reutilización" used across the AGE: no distortion of meaning, cite the source, mention last-update date, no
  suggestion of endorsement, keep the update-date metadata — [datos.gob.es aviso legal](https://datos.gob.es/es/aviso-legal)
- The datos.gob.es aviso legal page itself does not name CC BY 4.0 (the fetch found no CC reference) — [datos.gob.es aviso legal](https://datos.gob.es/es/aviso-legal)

### Inferences
- From prior knowledge **(unverified in this session; check the consolidated text at
  https://www.boe.es/buscar/act.php?id=BOE-A-1996-8930)**: LPI art. 13 excludes from copyright only laws,
  regulations, court decisions and acts/agreements/opinions of public bodies — an AESAN *scientific report* is not
  obviously an "acto" and is better treated as a protected work released under the reuse regime. Art. 133 sui generis
  right lasts 15 years from completion (art. 136) and restarts on a substantial new investment; art. 134 lets a lawful
  user extract *insubstantial* parts. Ley 37/2007 art. 3 excludes documents on which third parties hold IP rights, so
  a figure AESAN reproduces from SENC/Moreiras is not freed by AESAN's reuse licence.
- Practical rule for fdiet: citing one figure in a code comment or in the UI ("SENC 2018, Anexo 1.3") is low risk;
  committing the full annex as a CSV is extraction of a substantial part and needs a written licence.

### Gaps
- BOE article texts were not fetched in this session; article numbers above are from prior knowledge.

## 1. SENC 2018 — Guía de la alimentación saludable para atención primaria y colectivos ciudadanos

### Takeaway
The FESNAD-hosted PDF is **alive** (4.4 MB, downloaded 2026-10-02) and carries **no licence, no ©, no ISBN and no
reproduction clause** in its text; silence means all rights reserved. Permission must come from SENC (and possibly
Editorial Planeta for the commercial edition).

### Cited Findings
- PDF URL resolves and contains the full guide — [fesnad.org guiaSENC.pdf](https://www.fesnad.org/resources/files/guiaSENC.pdf)
- Credits page, verbatim: "LA GUIA ALIMENTARIA SENC NO TIENE PATROCINIOS COMERCIALES. Esta Guía ha sido elaborada con
  fondos propios de la Sociedad Española de Nutrición Comunitaria (SENC) mediante un convenio de edición con Editorial
  Planeta. Los posibles beneficios de la edición comercial, que se pondrá a la venta en librerías por parte de la
  Editorial Planeta, irán destinados íntegramente a la ONG "Nutrición sin Fronteras"." — [fesnad.org guiaSENC.pdf](https://www.fesnad.org/resources/files/guiaSENC.pdf)
- The annexes that matter: "ANEXO 1.2: Tamaño orientativo para las raciones de consumo recomendadas en población
  infantil y juvenil" (columns PESO RACIÓN EN CRUDO / MEDIDAS CASERAS), "ANEXO 1.3: Propuesta de raciones orientativas
  en el marco de la pirámide SENC - Adultos", "ANEXO 1.4: Frecuencias y pesos orientativos de raciones en función de la
  edad" — [fesnad.org guiaSENC.pdf](https://www.fesnad.org/resources/files/guiaSENC.pdf)
- A text search of the extracted PDF for "reprodu", "prohibid", "ISBN", "©", "derechos" found no rights clause
  (only the Planeta sentence above) — [fesnad.org guiaSENC.pdf](https://www.fesnad.org/resources/files/guiaSENC.pdf)
- Authors include Aranceta Bartrina (coordinator / dirección científica with Pérez Rodrigo and Serra Majem) — [fesnad.org guiaSENC.pdf](https://www.fesnad.org/resources/files/guiaSENC.pdf)

### Inferences
- Not loadable without permission. Request to SENC's secretariat, addressed to the scientific directors (Javier
  Aranceta Bartrina, Carmen Pérez Rodrigo, Lluís Serra Majem), asking a non-exclusive, free licence to reproduce
  Anexos 1.2–1.4 as a CSV in a public repository, with attribution, non-commercial, values unmodified; ask them to
  confirm Planeta's edition contract does not reserve those tables. Also ask whether FESNAD's hosting implies anything.
- Contact route: SENC site https://www.nutricioncomunitaria.org (contact form / secretaría técnica) **(unverified —
  contact email not fetched)**.

### Gaps
- SENC contact email and whether Planeta holds exclusive rights over the annexes — not found.
- No open-licence version of the annexes found.

## 2. DIAL (UCM Dpto. Nutrición y Ciencia de los Alimentos + Alce Ingeniería) — ~1,860 household measures

### Takeaway
Proprietary licensed software; the measures table is part of a paid product. **Alce Ingeniería has ceased activity
(2025) and the program is now managed by UCM's Departamento de Nutrición y Ciencia de los Alimentos (Facultad de
Farmacia)** — so UCM is now the only party to ask.

### Cited Findings
- UCM page: "Tabla de medidas caseras, con cerca de 1860 entradas, con unidades y raciones habitualmente utilizadas";
  developer "Departamento de Nutrición y Ciencia de los Alimentos (UCM) y Alceingeniería, S.A."; version 3.15 (2021);
  no licence/reuse terms on the page; contact via /idinutricion/contacto — [UCM programa DIAL](https://www.ucm.es/idinutricion/programa-dial)
- Search-result summary of the Alce/UCM pages: Alce Ingeniería ceased activity in 2025; DIAL now managed by the UCM
  department; registration (with optional webinar) costs €75 and includes installation — [Alce Ingeniería infodial](https://www.alceingenieria.net/infodial.htm); [UCM idinutricion-programa-dial](https://www.ucm.es/idinutricion/idinutricion-programa-dial) (summary from search; not fetched verbatim)
- Results "pueden ser exportadas a cualquier programa…" — export of a user's *results*, not of the reference tables — [UCM programa DIAL](https://www.ucm.es/idinutricion/programa-dial)

### Inferences
- Loading the measures table needs a written licence from UCM (department head, and the authors — Ortega, López Sobaler,
  Requejo et al. **(unverified author list)**). Copyright in the software/database may have been co-owned with Alce;
  with Alce dissolved, ask UCM to state it holds the rights (or via OTRI/UCM technology-transfer office).
- Request: free non-commercial licence for the household-measure table (food, measure, grams) for redistribution as a
  CSV under stated terms; attribution string they want.

### Gaps
- Exact UCM contact email and whether rights passed fully to UCM after Alce's closure.

## 3. FINUT/SEÑ — Guía fotográfica de porciones de alimentos consumidos en España (2019, ISBN 978-84-09-08860-7)

### Takeaway
A paid printed book (€69.26 incl. shipping/VAT); only a sample PDF is free. No open licence. Ask FINUT at info@finut.org.

### Cited Findings
- 944 photographs, 204 foods, 12 groups, "una tabla de pesos de las porciones", EFSA methodology; price "69,26 Euros";
  sample PDF https://www.finut.org/wp-content/uploads/2021/04/GuiaFotograficaAlimentos_Muestra.pdf; contact
  info@finut.org; FINUT, Avenida del Conocimiento 12, Edificio I+D Armilla, 3ª planta, PTS, 18016 Armilla (Granada) — [FINUT guía fotográfica](https://www.finut.org/guia-fotografica/)
- Also listed by SEÑ — [SEÑ publicación](https://www.sennutricion.org/es/publicacion/gua-fotogrfica-de-porciones-de-alimentos-consumidos-en-espaa); bibliographic record — [Dialnet](https://dialnet.unirioja.es/servlet/libro?codigo=920011)
- A ResearchGate upload exists, but that is not a licence — [ResearchGate](https://www.researchgate.net/publication/333176396_Guia_fotografica_de_porciones_de_alimentos_consumidos_en_Espana)

### Inferences
- Request to info@finut.org (Fundación Iberoamericana de Nutrición; co-editor SEÑ) for the portion-weight table only
  (no photos), non-commercial, attribution, unmodified.

### Gaps
- No copyright page text retrieved; no open-access validation paper with the full weight table identified.

## 4. Russolillo & Marques-Lopes — Sistema de intercambios (2011) and RENHyD 2009;13(3)

### Takeaway
The 2009 RENHyD item (pp. 137–139, "Listas de intercambios de alimentos para la confección de dietas y planificación de
menús") is **a short review/presentation of the system, not the lists**, and on Elsevier it is "all rights reserved"
(pre-open-access era). The lists themselves are only in the commercial book (Novadieta), a "patented" system — permission
from the authors/publisher is required.

### Cited Findings
- Article: Russolillo G, Marques Lopes I. Rev Esp Nutr Hum Diet 2009;13(3):137-139 — [Elsevier article page](https://www.elsevier.es/es-revista-revista-espanola-nutricion-humana-dietetica-283-articulo-listas-intercambios-alimentos-confeccion-dietas-13142124); [issue 13(3)](https://www.elsevier.es/es-revista-revista-espanola-nutricion-humana-dietetica-283-sumario-vol-13-num-3-X2173129209X77206)
- Page notice: "Copyright © 2026 Elsevier España SLU … Se reservan todos los derechos"; content describes the three-part
  system (methodology manual, photographic album, life-size portion guides) and does not reproduce the tables — [Elsevier article page](https://www.elsevier.es/es-revista-revista-espanola-nutricion-humana-dietetica-283-articulo-listas-intercambios-alimentos-confeccion-dietas-13142124)
- The article presents "a new patented system" ("sistema patentado") — [search summary of the same article](https://www.elsevier.es/es-revista-revista-espanola-nutricion-humana-dietetica-283-articulo-listas-intercambios-alimentos-confeccion-dietas-13142124)
- RENHyD today (renhyd.org, Academia Española de Nutrición y Dietética) publishes under **CC BY-SA 4.0**, e.g. the 5 al
  día 2019 article (doi 10.14306/renhyd.23.4.628) — [RENHyD article 628](https://www.renhyd.org/renhyd/article/view/628)
- The book: Sistema de intercambios… 2nd ed., Novadieta, 2011 — [plan.md R12a in repo; search result](https://www.renhyd.org/renhyd/issue/download/40/4)

### Inferences
- RENHyD's current CC BY-SA does **not** reach back to the 2009 Elsevier-era item (the Elsevier page asserts all rights
  reserved). Even if it did, the item has no lists. Defining "1 intercambio = 10 g HC/protein/fat" (what fdiet already
  uses) is a concept, not protected expression.
- Request: to the authors (Giuseppe Russolillo, Iva Marques-Lopes — Univ. de Zaragoza) and Novadieta / Academia Española
  de Nutrición y Dietética, for the exchange lists, non-commercial, attribution, unmodified, no "Sistema de intercambios®"
  branding.

### Gaps
- Whether a CC-licensed RENHyD paper reproducing the lists exists — not found. Novadieta contact not retrieved.
  Trademark/patent status of "Sistema de intercambios®" not verified.

## 5. Diabetes carbohydrate-ration tables

### Takeaway
Fundación para la Salud Novo Nordisk's table (S. Murillo, 3rd ed., 12 Sep 2022) is "Todos los derechos reservados".
Sant Joan de Déu and SED terms were not established. None is openly licensed as far as found.

### Cited Findings
- Murillo table: 3rd edition 12-09-2022; PDF http://www.fundacionparalasalud.org/upload/hidratos_carbono_textos/1/Tablas_Hidratos_3Edicion_FSNN.pdf;
  "© 2020 Fundación para la Salud Novo Nordisk. Todos los derechos reservados"; legal notice
  https://www.fundacionparalasalud.org/sabercomer/aviso/legal; contact Vía de los Poblados 3, 28033 Madrid, tel. 91 360 16 40,
  info@fundacionparalasalud.org — [Fundación para la Salud tabla](https://www.fundacionparalasalud.org/sabercomer/tabla_de_raciones_de_hidratos_de_carbono)
- Note the foundation is now "Fundación para la Salud Novo Nordisk" (fundacionparalasalud.org), formerly Fundación para la
  Diabetes — [Fundación para la Salud tabla](https://www.fundacionparalasalud.org/sabercomer/tabla_de_raciones_de_hidratos_de_carbono)
- Copies circulate on third-party sites (FADEX, Unión Diabéticos Alicante) — not a licence — [FADEX PDF](https://www.fadex.org/bddocumentos/3/TABLAHC.pdf); [UDA PDF](https://www.diabetesalicante.org/wp-content/uploads/2025/05/gallery-tabla-hidratos-de_carbono_serafin-murillo.pdf)
- Sant Joan de Déu guide (CIDI, diabetes.sjdhospitalbarcelona.org): ration table of 400+ foods and a calculator; no
  licence information found — [SJD raciones](https://diabetes.sjdhospitalbarcelona.org/es/diabetes-tipo-1/raciones-hidratos-carbono/1000)

### Inferences
- Request to info@fundacionparalasalud.org (and the author, Serafín Murillo) for the 10 g ration list, non-commercial,
  attribution, unmodified, with no Novo Nordisk branding implied (a pharma-funded source in a diet app is worth noting).
- SJD: ask via the CIDI site contact / Hospital Sant Joan de Déu Barcelona communication office.

### Gaps
- SJD and SED (Sociedad Española de Diabetes) licence pages not retrieved; SED materials not researched (call budget).

## 6. AESAN web legal notice (aviso legal) and reuse of AESAN reports

### Takeaway
The old URL (`/AECOSAN/web/aecosan_aviso_legal.htm`) is **404**; the current notice is at
**https://www.aesan.gob.es/aviso-legal**. It authorises reuse under the general AGE conditions (no distortion, cite source,
cite last-update date) — no CC licence. This covers AESAN's own reports (AESAN-2022-007, ENALIA) but not third-party
content inside them.

### Cited Findings
- Old URL returns HTTP 404 (2026-10-02) — [old aviso legal](https://www.aesan.gob.es/AECOSAN/web/aecosan_aviso_legal.htm)
- New site footer links "Aviso legal: https://www.aesan.gob.es/aviso-legal"; footer "© 2025 AESAN" — [AESAN home](https://www.aesan.gob.es/)
- Current notice (summarised by the fetch, partial quotes): portal design, code, logos and marks are protected IP of AESAN
  or collaborators; information is generally available for reuse provided "Queda prohibida en cualquier circunstancia la
  desnaturalización del contenido de la información", the source is cited and the last-update date mentioned; no CC licence
  named; IP contact comunicacionaesan@aesan.gob.es, C/ Alcalá 56, 28014 Madrid — [AESAN aviso legal](https://www.aesan.gob.es/aviso-legal)
- The previous notice (per search snippet) explicitly authorised commercial and non-commercial reuse under the three
  conditions and pointed to datos.gob.es/avisolegal — [old aviso legal (search snippet)](https://www.aesan.gob.es/AECOSAN/web/aecosan_aviso_legal.htm)

### Inferences
- AESAN-2022-007 recommendations and the AESAN/MEC 2010 school consensus can be loaded (as fdiet already does) with
  attribution + last-update date + no distortion. Caveat: the 2010 consensus was co-published with the Education ministry
  — ministry reuse rules are the same regime. Figures AESAN borrows from third parties (e.g. a table credited to SENC) are
  excluded by Ley 37/2007's third-party-IP carve-out.
- Get the exact verbatim clause into `reference-data/README.md` by saving the page (fetch gave a paraphrase).

### Gaps
- Verbatim full text of the new notice; whether it now mentions commercial use explicitly. Wayback not reachable here.

## 7. BEDCA (bedca.net, AESAN) — commercial use

### Takeaway
Terms unchanged since v1.0 (2010): reproduction allowed with attribution and no modification; any use beyond personal /
educational / non-commercial "de los datos en formato electrónico" needs AESAN/BEDCA's express authorisation "en base a las
condiciones que se remitirán a los interesados". The only published address is bedca.adm@gmail.com (errors); for
authorisation, write also to AESAN. No official bulk download exists; a datos.gob.es request for one is open.

### Cited Findings
- Verbatim: "La información que encuentren en ella no puede ser reproducida sin clara indicación de la fuente original:
  AESAN/BEDCA Base de Datos Española de Composición de Alimentos v1.0 (2010) y no podrán ser modificados ni alterado su
  significado original. La reproducción, traducción o cualquier uso que no sea personal, educacional o no comercial, de los
  datos en formato electrónico, estará sujeto a la autorización expresa de AESAN/BEDCA … en base a las condiciones que se
  remitirán a los interesados." Error reports: bedca.adm@gmail.com — [BEDCA UsoBD.pdf](https://www.bedca.net/bdpub/UsoBD.pdf)
- bedca.net footer: "Copyright © 2007 Consorcio BEDCA y Agencia Española de Seguridad Alimentaria y Nutrición, todos los
  derechos reservados" — [bedca.net/bdpub](https://www.bedca.net/bdpub/)
- datos.gob.es data request "Base de datos BEDCA" ("Base de datos de alimentos en formato digital, dbase, xml, CSV…"), filed
  14/04/2026, updated 29/09/2026, status "Assigned" to AESAN, ~67 supporters, no published answer — [datos.gob.es request](https://datos.gob.es/en/solicitud-de-datos/base-de-datos-bedca)

### Inferences
- For commercial use: email bedca.adm@gmail.com **and** AESAN (comunicacionaesan@aesan.gob.es, C/ Alcalá 56, 28014 Madrid,
  or AESAN's sede electrónica general registry) stating the product, that values are stored unmodified with units, the
  attribution shown, and the intended commercial model. Supporting the datos.gob.es request is a low-cost parallel step.
- Wording "uso … de los datos en formato electrónico" plausibly covers fdiet's CSV in the repo; non-commercial use is fine
  as is.

### Gaps
- No newer BEDCA release than v1.0 (2010) found.

## 8. Moreiras et al. Tablas de composición de alimentos (Pirámide) and ENALIA photographic atlas

### Takeaway
Moreiras is a commercial book (Ediciones Pirámide / Grupo Anaya), all rights reserved — needs publisher permission.
The ENALIA atlas (AESAN, 2014) is AESAN's own work and falls under the AESAN reuse notice (attribution, no distortion).

### Cited Findings
- ENALIA atlas: elaborated and validated by AESAN (direction Victoria Marcos Suárez, Josefa Rubio Mañas; Eva Galindo Moreno,
  Demométrica), published 2014 as a PDF on the AECOSAN site, 52 photo series of 4–6 portions with raw weights — [search summary / Scribd copy](https://www.scribd.com/document/351723499/Atlas-Fotogr-Fico-Enalia)
- UCM Innovadieta keeps a list of web resources on portions — [UCM recursos web raciones](https://www.ucm.es/innovadieta/recursos-web-raciones)

### Inferences
- ENALIA portion weights: loadable under the AESAN notice; photos too in principle, but check for third-party image rights.
- Moreiras: ask Ediciones Pirámide (permisos / derechos) and the authors (Moreiras, Carbajal, Cabrera, Cuadrado — UCM).
  Spanish books usually carry the CEDRO clause; CEDRO licences photocopying, not database extraction.

### Gaps
- Moreiras 2022 copyright page and ENALIA atlas current AESAN URL not fetched (call budget).

## 9. ASPCAT primera infància (0–3) 2022 and AEP complementary feeding 2018

### Takeaway
ASPCAT's materials in Scientia are **CC BY-NC-ND 4.0** (per search results): copying with attribution and non-commercial
is fine, but **NoDerivatives** makes extracting tables into a reworked CSV doubtful — ask ASPCAT. AEP 2018 licence not found.

### Cited Findings
- Scientia records for "L'alimentació saludable en la primera infància" (manual 2022 handle 11351/9237.3, tríptic, fullet,
  cartell) — search summary states CC BY-NC-ND 4.0 — [Scientia manual](https://scientiasalut.gencat.cat/handle/11351/9237.3); [Scientia cartell](https://scientiasalut.gencat.cat/handle/11351/11020?show=full)
- The Scientia record page returned "Access Denied" to direct fetch, so dc.rights was not read verbatim — [Scientia manual](https://scientiasalut.gencat.cat/handle/11351/9237.3?show=full)
- AEP "Recomendaciones de la AEP sobre alimentación complementaria", Comité de Lactancia Materna y Comité de Nutrición, coord.
  Marta Gómez Fernández-Vegue, 9 Nov 2018 — [AEP PDF](https://static.aeped.es/recomendaciones_aep_sobre_alimentacio_n_complementaria_nov2018_v3_final_0d83dbbd5a.pdf)

### Inferences
- Under CC BY-NC-ND, a verbatim copy of a table (unaltered) with attribution in a non-commercial repo is arguably allowed;
  reformatting it into CSV rows may be an adaptation — get a written OK from ASPCAT (salutpublica.gencat.cat). This mirrors
  how 5 al día's CC BY-SA forces ShareAlike.
- AEP: treat as all rights reserved; ask AEP Comité de Nutrición.

### Gaps
- Verbatim dc.rights; AEP licence; AEP/ASPCAT contact emails.

## 10. CEU San Pablo — TABULA® (processed products)

### Takeaway
Same model as BEDCA: free to consult; any use other than personal, educational or non-commercial of the electronic data
needs express authorisation from TABULA's administrators. The site aviso legal adds that only personal private use is allowed
and commercial reproduction is prohibited. Contact ias@ceu.es.

### Cited Findings
- Version "TABULA® 3.0, 2025", published Sept 2025 — [TABULA presentación](https://ias.ceu.es/tabula-bbdd/presentacion/)
- Conditions (search summary): ~6,500 products (~80 % of the Spanish market); "La reproducción, traducción o cualquier uso
  que no sea personal, educacional o no comercial de los datos en formato electrónico, estará sujeto a la autorización expresa
  de los administradores de TABULA®, en base a las condiciones que se remitirán a los interesados" (paraphrase of the search
  snippet); industry contact ias@ceu.es — [TABULA resumen PDF](https://ias.ceu.es/wp-content/uploads/Tabula_Resumen_ESP.pdf)
- Site aviso legal: owner Fundación Universitaria San Pablo CEU, CIF G28423275, C/ Isaac Peral 58, 28040 Madrid; reproduction
  "con fines comerciales … sin la autorización" prohibited; users may view, print, copy and store "para su uso personal y
  privado" — [CEU IAS aviso legal](https://ias.ceu.es/aviso-legal/)

### Inferences
- Bulk-loading TABULA into a public repo goes beyond "personal y privado" in the site notice even if the data terms tolerate
  non-commercial use — ask ias@ceu.es (Instituto Universitario CEU Alimentación y Sociedad) for a non-commercial
  redistribution licence. TABULA also mirrors branded label data, which overlaps fdiet's `food_items`.

### Gaps
- Verbatim TABULA conditions page not fetched (only the search snippet and resumen URL).

## Request template (applies to every source above)

### Takeaway
Every request should state the same few things so the answer is a usable licence.

### Cited Findings
- AESAN/BEDCA and TABULA both say conditions "se remitirán a los interesados" — i.e. they expect a request describing the
  use — [BEDCA UsoBD.pdf](https://www.bedca.net/bdpub/UsoBD.pdf)

### Inferences
- Include: who you are; the project (open-source, non-commercial, repo URL); the exact table/pages; that values are stored
  verbatim with units and source/page per row; the attribution shown in UI and repo; that the repo is public (third parties
  can download the CSV); the licence the repo uses; whether future commercial use is foreseen; ask for a written,
  non-exclusive, royalty-free licence and the exact attribution string. Keep the reply in `reference-data/<source>/`.

### Gaps
- None beyond those listed per source.
