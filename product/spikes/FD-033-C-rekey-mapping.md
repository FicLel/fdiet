# FD-033 phase C — BEDCA → CIQUAL / BLS picks for the reference rows (approval list)

Status: **approved by the user 2026-10-03** (all rows, incl. the 1 LOW and 4 MEDIUM as proposed) · Written 2026-10-03 by the backend agent · Decisions 15–17

Every BEDCA food a reference CSV named — **60 foods, all in `reference-data/5aldia-2019`** (60 of its 80
rations, all 62 of its household measures); `aesan-2022` and `aesan-mec-2010` named none. For each,
the composition food the CSVs now name by `composition_source,composition_code`.

**Method.** The phase B prefill (BEDCA's English name against both tables' English names, CIQUAL first,
BLS where only it has the food or the state written), then the crosswalk row
(`reference-data/composition/composition-es/links.csv`) that names the same food in the same state.
59 of the 60 picks are crosswalk rows the user approved on 2026-10-03; **one is new** (`reviewed=false`).
No row is left unkeyed: every food had an equivalent.

**Edible portion.** Every 5 al día row is `NET_EDIBLE`, so no pick's edible portion weighs anything
today; it is listed because a gross weight of the same food would use it.

**Accepted cost (decision 15).** Until phase D gives ingredients a composition food, these id-keyed
rows weigh and count **no** BEDCA-matched ingredient; family + keyword rows keep working.

## To review first (LOW / MEDIUM)

| Conf. | BEDCA id | BEDCA name | → source code | Original name | English name | name_es | Edible portion (fdc_id) | Note | CSV rows (5ALDIA-2019:…) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LOW | 1167 | Piña, enlatada en su jugo | CIQUAL 13716 | Ananas au jus d'ananas, appertisé, égoutté | Pineapple, in pineapple juice, canned, drained | Piña, en su jugo, en conserva, escurrida (approved 2026-10-03) | 1.00 (167767) | New crosswalk row, `reviewed=false`. Phase B listed "piña en su jugo" as having no equivalent, but CIQUAL 13716 is pineapple canned in its own juice (drained); 13717 is the same not drained. A 5 al día slice is the drained fruit. Approved 2026-10-03; added to V16. | PINA-EN-SU-JUGO, M-PINA-EN-SU-JUGO-RODAJA |
| MEDIUM | 2234 | Melón | CIQUAL 13742 | Melon miel ou melon honeydew, chair sans peau, sans pépins, cru | Melon, honeydew, flesh without skin, without seeds, raw | Melón, crudo | 0.46 (169911) | CIQUAL's honeydew melon for a generic "Melón": the Spanish piel de sapo type is the nearest CIQUAL row. | MELON, M-MELON-RODAJA-MEDIUM |
| MEDIUM | 2236 | Nectarina | CIQUAL 13148 | Nectarine ou brugnon, jaune, chair et peau, sans noyau, crue | Nectarine, yellow, flesh and skin, pitted, raw | Nectarina, cruda | 0.91 (169914) | CIQUAL 13148 is the yellow nectarine (the crosswalk row); CIQUAL also has 13399, nectarine average, with no Spanish name yet. | NECTARINA, M-NECTARINA-UNIDAD-SMALL |
| MEDIUM | 2246 | Pomelo | BLS F604100 | Grapefruit roh | Grapefruit raw | Pomelo, crudo | 0.51 (174673) | The approved crosswalk row is BLS ("CIQUAL only has pummelo"), but CIQUAL 13040 is grapefruit too (French "Pomelo (dit Pamplemousse)"). CIQUAL-first would pick 13040; kept BLS because that row is the one approved. | POMELO, M-POMELO-UNIDAD-MEDIUM |
| MEDIUM | 2423 | Tomate, maduro, pelado y triturado, enlatado | CIQUAL 20169 | Tomate, chair, appertisée | Tomato flesh without skin, canned | Tomate, pelado y triturado, en conserva | 1.00 (170051) | CIQUAL 20169 "Tomato flesh without skin, canned" for crushed tinned tomato; BLS G568900 (peeled, canned) and CIQUAL 20048 (peeled, canned, drained) are the alternatives. | TOMATE-TRITURADO-EN-CONSERVA, M-TOMATE-TRITURADO-EN-CONSERVA-CUCHARADA_SOPERA |

## High confidence

| Conf. | BEDCA id | BEDCA name | → source code | Original name | English name | name_es | Edible portion (fdc_id) | Note | CSV rows (5ALDIA-2019:…) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| HIGH | 849 | Soja, germinada, en conserva | BLS H620902 | Sojabohnensprossen/Sojabohnenkeimlinge, Konserve, abgetropft | Soya bean sprouts canned, drained | Soja, germinada, en conserva, escurrida | 1.00 (170079) | BLS: CIQUAL has canned mung bean sprouts only. | SOJA-GERMINADA-EN-CONSERVA, M-SOJA-GERMINADA-EN-CONSERVA-CUCHARADA_SOPERA |
| HIGH | 859 | Coliflor, congelada, cruda | CIQUAL 20082 | Chou-fleur, surgelé, cru | Cauliflower, frozen, raw | Coliflor, congelada, cruda | 1.00 (170398) | Frozen raw cauliflower, as the row says (CIQUAL 20082, not the fresh 20016). | COLIFLOR-CONGELADA, M-COLIFLOR-CONGELADA-PLATO-SMALL |
| HIGH | 880 | Nabo, pelado, crudo | CIQUAL 20064 | Navet, pelé, cru | Turnip, peeled, raw | Nabo, pelado, crudo | 0.81 (170465) | Same food, same state; the approved crosswalk row. | NABOS, M-NABOS-UNIDAD-MEDIUM |
| HIGH | 882 | Palmito, en conserva | CIQUAL 20018 | Coeur de palmier, appertisé, égoutté | Palm heart, canned, drained | Palmito, en conserva, escurrido | 1.00 (168569) | Same food, same state; the approved crosswalk row. | PALMITO-EN-CONSERVA, M-PALMITO-EN-CONSERVA-UNIDAD |
| HIGH | 914 | Frambuesa, cruda | CIQUAL 13015 | Framboise, crue | Raspberry, raw | Frambuesa, cruda | 0.96 (167755) | Same food, same state; the approved crosswalk row. | FRAMBUESA, M-FRAMBUESA-PLATO-SMALL |
| HIGH | 946 | Melocotón, desecado | CIQUAL 13118 | Pêche, chair et peau, sans noyau, sèche | Peach, flesh and skin, pitted, dried | Melocotón, desecado | 1.00 (169934) | Same food, same state; the approved crosswalk row. | MELOCOTON-SECO, M-MELOCOTON-SECO-PORCION-SMALL |
| HIGH | 999 | Grosella, cruda | CIQUAL 13019 | Groseille, crue | Red currant, raw | Grosella roja, cruda | 0.98 (173964) | Same food, same state; the approved crosswalk row. | GROSELLA, M-GROSELLA-PLATO-SMALL |
| HIGH | 1006 | Membrillo, crudo | CIQUAL 13010 | Coing, cru | Quince, raw | Membrillo, crudo | 0.61 (168163) | Same food, same state; the approved crosswalk row. | MEMBRILLO, M-MEMBRILLO-UNIDAD-MEDIUM |
| HIGH | 1008 | Papaya, cruda | CIQUAL 13035 | Papaye, chair sans peau, crue | Papaya, flesh without skin, raw | Papaya, cruda | 0.62 (169926) | Peeled flesh, matching "pelada y troceada". | PAPAYA-PELADA-Y-TROCEADA, M-PAPAYA-PELADA-Y-TROCEADA-UNIDAD-LARGE, M-PAPAYA-PELADA-Y-TROCEADA-UNIDAD-MEDIUM |
| HIGH | 1017 | Uva negra, cruda | CIQUAL 13045 | Raisin noir, cru | Grape, red, raw | Uva negra, cruda | 0.96 (174683) | Black grape read as CIQUAL's red grape. | UVAS-NEGRAS, M-UVAS-NEGRAS-UNIDAD-MEDIUM |
| HIGH | 1172 | Ciruela, con piel, cruda | CIQUAL 13100 | Prune, sans noyau, crue | Plum, flesh and skin, pitted, raw | Ciruela, cruda | 0.94 (169949) | Same food, same state; the approved crosswalk row. | CIRUELAS, M-CIRUELAS-UNIDAD-SMALL |
| HIGH | 1175 | Guisante, congelado, crudo | CIQUAL 20084 | Petits pois, surgelés, crus | Garden peas, frozen, raw | Guisante, congelado, crudo | 1.00 (170016) | Frozen raw peas, as the row says. | GUISANTE-FRESCO-CONGELADO, M-GUISANTE-FRESCO-CONGELADO-PLATO-SMALL |
| HIGH | 1176 | Alcachofa, cruda | CIQUAL 20052 | Artichaut, cru | Artichoke, globe, raw | Alcachofa, cruda | 0.40 (169205) | Same food, same state; the approved crosswalk row. | ALCACHOFAS, M-ALCACHOFAS-UNIDAD-MEDIUM |
| HIGH | 1180 | Col de bruselas, cruda | CIQUAL 20058 | Chou de Bruxelles, cru | Brussels sprout, raw | Col de Bruselas, cruda | 0.90 (170383) | Same food, same state; the approved crosswalk row. | COLES-DE-BRUSELAS, M-COLES-DE-BRUSELAS-UNIDAD |
| HIGH | 1186 | Mango, crudo | CIQUAL 13025 | Mangue, chair sans peau, sans noyau, crue | Mango, flesh without skin, pitted, raw | Mango, crudo | 0.71 (169910) | Same food, same state; the approved crosswalk row. | MANGO, M-MANGO-UNIDAD |
| HIGH | 2193 | Guisantes en conserva | CIQUAL 20036 | Petits pois, appertisés, égouttés | Garden peas, canned, drained | Guisante, en conserva, escurrido | 1.00 (170015) | Same food, same state; the approved crosswalk row. | GUISANTE-EN-CONSERVA, M-GUISANTE-EN-CONSERVA-PLATO-SMALL |
| HIGH | 2198 | Judía verde, cruda | CIQUAL 20061 | Haricot vert, cru | French bean, raw | Judía verde, cruda | 0.88 (169961) | The row says fresh or frozen; the fresh raw bean is used for both. | JUDIA-VERDE-FRESCA-CONGELADA, M-JUDIA-VERDE-FRESCA-CONGELADA-PLATO-SMALL |
| HIGH | 2212 | Acelga, cruda | BLS G230100 | Mangold roh | Chard/leaf beet, raw | Acelga, cruda | 0.92 (169991) | BLS: CIQUAL publishes only the chard stalk. | ACELGA, M-ACELGA-PLATO-MEDIUM |
| HIGH | 2216 | Aguacate | CIQUAL 13004 | Avocat, chair sans peau, sans noyau, cru | Avocado, flesh without skin, pitted, raw | Aguacate | 0.74 (171705) | Same food, same state; the approved crosswalk row. | AGUACATE, M-AGUACATE-UNIDAD-MEDIUM |
| HIGH | 2218 | Albaricoque | CIQUAL 13000 | Abricot, dénoyauté, cru | Apricot, pitted, raw | Albaricoque, crudo | 0.93 (171697) | Same food, same state; the approved crosswalk row. | ALBARICOQUE, M-ALBARICOQUE-UNIDAD-MEDIUM |
| HIGH | 2220 | Cereza | CIQUAL 13008 | Ceriser, chair et peau, sans noyau, crue | Cherry, flesh and skin, pitted, raw | Cereza, cruda | 0.92 (171719) | Same food, same state; the approved crosswalk row. | CEREZAS, M-CEREZAS-PLATO-SMALL, M-CEREZAS-UNIDAD |
| HIGH | 2225 | Fresa | CIQUAL 13014 | Fraise, crue | Strawberry, raw | Fresa, cruda | 0.94 (167762) | Same food, same state; the approved crosswalk row. | FRESA-FRESON |
| HIGH | 2226 | Granada | CIQUAL 13018 | Grenade, chair sans peau, avec pépins, crue | Pomegranate, flesh without skin, with seeds, raw | Granada, cruda | 0.56 (169134) | Same food, same state; the approved crosswalk row. | GRANADA, M-GRANADA-UNIDAD-SMALL |
| HIGH | 2227 | Higos y brevas | CIQUAL 13012 | Figue, crue | Fig, raw | Higo, crudo | 0.99 (173021) | Same food, same state; the approved crosswalk row. | HIGO-FRESCO, M-HIGO-FRESCO-UNIDAD-SMALL |
| HIGH | 2228 | Kiwi | CIQUAL 13021 | Kiwi, chair sans peau, avec pépins, cru | Kiwi fruit, flesh without skin, with seeds, raw | Kiwi, crudo | 0.76 (168153) | Same food, same state; the approved crosswalk row. | KIWI, M-KIWI-UNIDAD-MEDIUM |
| HIGH | 2229 | Mandarina | CIQUAL 13024 | Clémentine ou mandarine, chair sans peau, sans pépins, crue | Clementine or Mandarin orange, flesh without skin, without seeds, raw | Mandarina, cruda | 0.74 (169105) | Same food, same state; the approved crosswalk row. | MANDARINA, M-MANDARINA-UNIDAD-SMALL, M-MANDARINA-UNIDAD-LARGE |
| HIGH | 2231 | Manzana | CIQUAL 13039 | Pomme, chair et peau, crue | Apple, flesh and skin, raw | Manzana, cruda | 0.90 (171688) | Same food, same state; the approved crosswalk row. | MANZANA, M-MANZANA-UNIDAD-MEDIUM |
| HIGH | 2232 | Melocotón | CIQUAL 13043 | Pêche, chair et peau, sans noyau, crue | Peach, flesh and skin, pitted, raw | Melocotón, crudo | 0.96 (169928) | Same food, same state; the approved crosswalk row. | MELOCOTON, M-MELOCOTON-UNIDAD-SMALL |
| HIGH | 2235 | Naranja | CIQUAL 13034 | Orange, chair sans peau, sans pépins, crue | Orange, flesh without skin, without seeds, raw | Naranja, cruda | 0.73 (169097) | Same food, same state; the approved crosswalk row. | NARANJA, M-NARANJA-UNIDAD-MEDIUM |
| HIGH | 2241 | Pera | CIQUAL 13037 | Poire, chair et peau, crue | Pear, flesh and skin, raw | Pera, cruda | 0.90 (169118) | Same food, same state; the approved crosswalk row. | PERA, M-PERA-UNIDAD-MEDIUM |
| HIGH | 2242 | Piña | CIQUAL 13002 | Ananas, chair sans peau, cru | Pineapple, flesh without skin, raw | Piña, cruda | 0.51 (169124) | Flesh without skin, matching "sin piel". | PINA-SIN-PIEL, M-PINA-SIN-PIEL-RODAJA-MEDIUM |
| HIGH | 2245 | Plátano | CIQUAL 13005 | Banane, chair sans peau, crue | Banana, flesh without skin, raw | Plátano, crudo | 0.64 (173944) | Same food, same state; the approved crosswalk row. | PLATANO, M-PLATANO-UNIDAD-MEDIUM |
| HIGH | 2247 | Sandía | CIQUAL 13036 | Pastèque, chair sans peau, sans pépins, crue | Watermelon, flesh without skin, without seeds, raw | Sandía, cruda | 0.52 (167765) | Same food, same state; the approved crosswalk row. | SANDIA, M-SANDIA-RODAJA-MEDIUM |
| HIGH | 2249 | Uva blanca | CIQUAL 13044 | Raisin blanc, à gros grain (type Italia ou Dattier), cru | Grape, white, raw | Uva blanca, cruda | 0.96 (174683) | Same food, same state; the approved crosswalk row. | UVAS-BLANCAS, M-UVAS-BLANCAS-UNIDAD-MEDIUM |
| HIGH | 2251 | Uva pasa | CIQUAL 13046 | Raisin sec | Raisin | Uva pasa | 1.00 (168165) | Same food, same state; the approved crosswalk row. | PASAS-SECAS |
| HIGH | 2365 | Zumo de uva | CIQUAL 2016 | Jus de raisin, pur jus | Grape juice, pure juice | Uva, zumo | 1.00 (173042) | Same food, same state; the approved crosswalk row. | ZUMO-DE-UVA, M-ZUMO-DE-UVA-VASO-SMALL |
| HIGH | 2366 | Apio, crudo | CIQUAL 20023 | Céleri branche, cru | Celery stalk, raw | Apio, crudo | 0.89 (169988) | Same food, same state; the approved crosswalk row. | APIO |
| HIGH | 2368 | Arandano | CIQUAL 13028 | Myrtille, crue | Blueberry, raw | Arándano, crudo | 0.95 (171711) | Same food, same state; the approved crosswalk row. | ARANDANOS, M-ARANDANOS-PLATO-SMALL |
| HIGH | 2370 | Berenjena | CIQUAL 20053 | Aubergine, crue | Eggplant, raw | Berenjena, cruda | 0.81 (169228) | Same food, same state; the approved crosswalk row. | BERENJENA, M-BERENJENA-UNIDAD-MEDIUM |
| HIGH | 2376 | Calabacín | CIQUAL 20020 | Courgette, chair et peau, crue | Courgette or zucchini, flesh and skin, raw | Calabacín, crudo | 0.95 (169291) | Same food, same state; the approved crosswalk row. | CALABACIN, M-CALABACIN-UNIDAD-SMALL |
| HIGH | 2378 | Calabaza, cruda | CIQUAL 20044 | Potiron, cru | Pumpkin, raw | Calabaza, cruda | 0.70 (168448) | Same food, same state; the approved crosswalk row. | CALABAZA, M-CALABAZA-PLATO-SMALL |
| HIGH | 2380 | Cardo | CIQUAL 20054 | Cardon, cru | Cardoon, raw | Cardo, crudo | 0.49 (169981) | Same food, same state; the approved crosswalk row. | CARDO, M-CARDO-PLATO-SMALL |
| HIGH | 2381 | Cebolla | CIQUAL 20034 | Oignon, cru | Onion, raw | Cebolla, cruda | 0.90 (170000) | Same food, same state; the approved crosswalk row. | CEBOLLA, M-CEBOLLA-UNIDAD-MEDIUM |
| HIGH | 2384 | Champiñon | CIQUAL 20056 | Champignon de Paris ou champignon de couche, cru | Button mushroom or cultivated mushroom, raw | Champiñón, crudo | 0.97 (169251) | Same food, same state; the approved crosswalk row. | CHAMPINONES, M-CHAMPINONES-UNIDAD-MEDIUM |
| HIGH | 2386 | Col blanca, cruda | CIQUAL 20116 | Chou blanc, cru | White cabbage, raw | Col blanca, cruda | 0.80 (169975) | Same food, same state; the approved crosswalk row. | COL-REPOLLO, M-COL-REPOLLO-PLATO-SMALL |
| HIGH | 2388 | Endibia | CIQUAL 20026 | Endive, crue | Chicory, raw | Endibia, cruda | 0.89 (170404) | Same food, same state; the approved crosswalk row. | ENDIBIA, M-ENDIBIA-UNIDAD-MEDIUM |
| HIGH | 2390 | Escarola | CIQUAL 20090 | Scarole, crue | Escaroles, raw | Escarola, cruda | 0.86 (168412) | Same food, same state; the approved crosswalk row. | ESCAROLA, M-ESCAROLA-PLATO-LARGE |
| HIGH | 2393 | Esparragos blancos en conserva | CIQUAL 20076 | Asperge, appertisée, égouttée | Asparagus, canned, drained | Espárrago blanco, en conserva | 1.00 (169207) | CIQUAL does not say white, but canned asparagus is the white kind. | ESPARRAGO-BLANCO-EN-CONSERVA, M-ESPARRAGO-BLANCO-EN-CONSERVA-UNIDAD, M-ESPARRAGO-BLANCO-EN-CONSERVA-PLATO-MEDIUM |
| HIGH | 2398 | Judias verdes en conserva | CIQUAL 20062 | Haricot vert, appertisé, égoutté | French bean, canned, drained | Judía verde, en conserva, escurrida | 1.00 (169143) | Same food, same state; the approved crosswalk row. | JUDIA-VERDE-EN-CONSERVA, M-JUDIA-VERDE-EN-CONSERVA-PLATO-SMALL |
| HIGH | 2399 | Lechuga | CIQUAL 20031 | Laitue, crue | Lettuce, raw | Lechuga, cruda | 0.64 (169249) | Same food, same state; the approved crosswalk row. | LECHUGA, M-LECHUGA-PLATO-LARGE |
| HIGH | 2407 | Pepino | CIQUAL 20019 | Concombre, chair et peau, cru | Cucumber, flesh and skin, raw | Pepino, crudo | 0.97 (168409) | Same food, same state; the approved crosswalk row. | PEPINO, M-PEPINO-PLATO-MEDIUM |
| HIGH | 2409 | Pimiento rojo, crudo | CIQUAL 20087 | Poivron rouge, cru | Sweet pepper, red, raw | Pimiento rojo, crudo | 0.82 (170108) | Same food, same state; the approved crosswalk row. | PIMIENTO-ROJO, M-PIMIENTO-ROJO-UNIDAD-MEDIUM |
| HIGH | 2411 | Puerro, crudo y congelado | CIQUAL 20039 | Poireau, cru | Leek, raw | Puerro, crudo | 0.44 (169246) | Same food, same state; the approved crosswalk row. | PUERRO, M-PUERRO-UNIDAD-LARGE |
| HIGH | 2421 | Tomate | CIQUAL 20385 | Tomate sans précision, crue (aliment moyen) | Tomato, raw (average) | Tomate, crudo | 0.91 (170457) | Same food, same state; the approved crosswalk row. | TOMATE-MADURO-CRUDO, M-TOMATE-MADURO-CRUDO-UNIDAD-MEDIUM, M-TOMATE-MADURO-CRUDO-PLATO-MEDIUM |
| HIGH | 2424 | Zanahoria, cruda | CIQUAL 20009 | Carotte, crue | Carrot, raw | Zanahoria, cruda | 0.89 (170393) | Same food, same state; the approved crosswalk row. | ZANAHORIA, M-ZANAHORIA-UNIDAD-LARGE |

## What approving means

- Approve the list → the user sets `reviewed=true` on the new crosswalk row (CIQUAL 13716) and the picks stand.
- Reject a pick → change `composition_source,composition_code` in both 5 al día CSVs (and `links.csv` if
  the replacement has no crosswalk row), then `POST /api/reference/sync`. The sync rewrites published
  rows by code, so no migration is needed for published rows.
- The V16 migration carries the same mapping (BEDCA 1167 included since 2026-10-03) only to re-key rows already stored —
  published ones until the next sync, and any diet or global criterion (none in the dev database today).
