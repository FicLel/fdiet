-- FD-033 phase C: reference rows name a composition food (CIQUAL 2025 / BLS 4.0,
-- `composition_foods`) instead of a BEDCA food.
--
-- `ref_rations.bedca_food_id` and `ref_food_measures.bedca_food_id` are replaced
-- by `composition_food_id`; no BEDCA column stays beside it. That covers all three
-- kinds of measure row: published (a source and a code), one diet's criterion
-- (`diet_id`) and the nutritionist's global criterion (`global_criterion`).
--
-- The rows are re-keyed through the mapping below: one composition food per BEDCA
-- food the reference CSVs named, each the crosswalk row
-- (`reference-data/composition/composition-es/links.csv`) for the same food in the
-- same state, named by `(source, source_code)` because `composition_foods.id`
-- differs per installation. The approval list is
-- `product/spikes/FD-033-C-rekey-mapping.md`, approved by the project owner on
-- 2026-10-03 as proposed — including BEDCA 1167 (Piña, enlatada en su jugo) to
-- the crosswalk row phase C added for it (CIQUAL 13716, drained).
--
-- * Published rows are re-keyed where the mapping and the loaded composition
--   foods allow. One that cannot be is made inert — no food, no family, so it
--   covers nothing — rather than left covering a whole family; the reference sync
--   rewrites every published row by its code from the CSVs, which now carry the
--   composition key, so the next sync (one runs at startup) puts it right.
-- * The nutritionist's criteria are hers, and the sync never writes them. One
--   whose BEDCA food has no equivalent here is not re-pointed by guess: the
--   migration stops before changing anything and names it. Re-point or delete
--   that criterion, then run the migration again (after `flywayRepair`, since
--   MySQL cannot roll the failed attempt back).
--
-- `criterion_key` and `ck_ref_food_measures_global` (V14) name the food column,
-- so both are dropped and rebuilt on the new one, as is the index the criteria
-- are read by.

CREATE TEMPORARY TABLE v16_bedca_composition (
    bedca_food_id BIGINT      NOT NULL,
    source        VARCHAR(16) NOT NULL,
    source_code   VARCHAR(32) NOT NULL,
    PRIMARY KEY (bedca_food_id)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO v16_bedca_composition (bedca_food_id, source, source_code) VALUES
    (849, 'BLS', 'H620902'), (859, 'CIQUAL', '20082'), (880, 'CIQUAL', '20064'),
    (882, 'CIQUAL', '20018'), (914, 'CIQUAL', '13015'), (946, 'CIQUAL', '13118'),
    (999, 'CIQUAL', '13019'), (1006, 'CIQUAL', '13010'), (1008, 'CIQUAL', '13035'),
    (1017, 'CIQUAL', '13045'), (1167, 'CIQUAL', '13716'), (1172, 'CIQUAL', '13100'),
    (1175, 'CIQUAL', '20084'), (1176, 'CIQUAL', '20052'), (1180, 'CIQUAL', '20058'),
    (1186, 'CIQUAL', '13025'), (2193, 'CIQUAL', '20036'), (2198, 'CIQUAL', '20061'),
    (2212, 'BLS', 'G230100'), (2216, 'CIQUAL', '13004'), (2218, 'CIQUAL', '13000'),
    (2220, 'CIQUAL', '13008'), (2225, 'CIQUAL', '13014'), (2226, 'CIQUAL', '13018'),
    (2227, 'CIQUAL', '13012'), (2228, 'CIQUAL', '13021'), (2229, 'CIQUAL', '13024'),
    (2231, 'CIQUAL', '13039'), (2232, 'CIQUAL', '13043'), (2234, 'CIQUAL', '13742'),
    (2235, 'CIQUAL', '13034'), (2236, 'CIQUAL', '13148'), (2241, 'CIQUAL', '13037'),
    (2242, 'CIQUAL', '13002'), (2245, 'CIQUAL', '13005'), (2246, 'BLS', 'F604100'),
    (2247, 'CIQUAL', '13036'), (2249, 'CIQUAL', '13044'), (2251, 'CIQUAL', '13046'),
    (2365, 'CIQUAL', '2016'), (2366, 'CIQUAL', '20023'), (2368, 'CIQUAL', '13028'),
    (2370, 'CIQUAL', '20053'), (2376, 'CIQUAL', '20020'), (2378, 'CIQUAL', '20044'),
    (2380, 'CIQUAL', '20054'), (2381, 'CIQUAL', '20034'), (2384, 'CIQUAL', '20056'),
    (2386, 'CIQUAL', '20116'), (2388, 'CIQUAL', '20026'), (2390, 'CIQUAL', '20090'),
    (2393, 'CIQUAL', '20076'), (2398, 'CIQUAL', '20062'), (2399, 'CIQUAL', '20031'),
    (2407, 'CIQUAL', '20019'), (2409, 'CIQUAL', '20087'), (2411, 'CIQUAL', '20039'),
    (2421, 'CIQUAL', '20385'), (2423, 'CIQUAL', '20169'), (2424, 'CIQUAL', '20009');

-- The mapping resolved to this database's ids. A pick whose food is not loaded
-- resolves to nothing, exactly like a BEDCA food without a pick.
CREATE TEMPORARY TABLE v16_food_map (
    bedca_food_id       BIGINT NOT NULL,
    composition_food_id BIGINT NOT NULL,
    PRIMARY KEY (bedca_food_id)
);

INSERT INTO v16_food_map (bedca_food_id, composition_food_id)
SELECT m.bedca_food_id, c.id
FROM v16_bedca_composition m
         JOIN composition_foods c ON c.source = m.source AND c.source_code = m.source_code;

-- The criteria check comes first, so a refusal leaves every table as it was.
DROP PROCEDURE IF EXISTS v16_require_criteria_rekeyable;

DELIMITER //
CREATE PROCEDURE v16_require_criteria_rekeyable()
BEGIN
    DECLARE stranded TEXT;
    SELECT GROUP_CONCAT(CONCAT('id ', m.id, ' (', m.food_label, ')') ORDER BY m.id SEPARATOR ', ')
    INTO stranded
    FROM ref_food_measures m
             LEFT JOIN v16_food_map f ON f.bedca_food_id = m.bedca_food_id
    WHERE (m.diet_id IS NOT NULL OR m.global_criterion)
      AND m.bedca_food_id IS NOT NULL
      AND f.composition_food_id IS NULL;
    IF stranded IS NOT NULL THEN
        SET @v16_message = LEFT(CONCAT('V16: no composition food for criteria ', stranded,
                                       '. Re-point or delete them, then migrate again'), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @v16_message;
    END IF;
END //
DELIMITER ;

CALL v16_require_criteria_rekeyable();
DROP PROCEDURE v16_require_criteria_rekeyable;

-- The new columns, indexed for the lookups that read them.
ALTER TABLE ref_rations
    ADD COLUMN composition_food_id BIGINT NULL AFTER bedca_food_id;
CREATE INDEX idx_ref_rations_composition ON ref_rations (composition_food_id);
ALTER TABLE ref_rations
    ADD CONSTRAINT fk_ref_rations_composition
        FOREIGN KEY (composition_food_id) REFERENCES composition_foods (id);

ALTER TABLE ref_food_measures
    ADD COLUMN composition_food_id BIGINT NULL AFTER bedca_food_id;
CREATE INDEX idx_ref_food_measures_composition ON ref_food_measures (composition_food_id);
ALTER TABLE ref_food_measures
    ADD CONSTRAINT fk_ref_food_measures_composition
        FOREIGN KEY (composition_food_id) REFERENCES composition_foods (id);

-- Re-key.
UPDATE ref_rations r
    JOIN v16_food_map f ON f.bedca_food_id = r.bedca_food_id
SET r.composition_food_id = f.composition_food_id;

UPDATE ref_food_measures m
    JOIN v16_food_map f ON f.bedca_food_id = m.bedca_food_id
SET m.composition_food_id = f.composition_food_id;

-- A published row that named a BEDCA food and could not be re-keyed covers nothing
-- until the sync rewrites it (criteria cannot reach here: the check above).
UPDATE ref_rations
SET food_category = NULL,
    keywords      = NULL
WHERE bedca_food_id IS NOT NULL
  AND composition_food_id IS NULL;

UPDATE ref_food_measures
SET food_category = NULL,
    keywords      = NULL
WHERE bedca_food_id IS NOT NULL
  AND composition_food_id IS NULL;

-- The V14 criterion key, check and index name the BEDCA column: drop them first.
ALTER TABLE ref_food_measures DROP INDEX uk_ref_food_measures_criterion;
ALTER TABLE ref_food_measures DROP CHECK ck_ref_food_measures_global;
ALTER TABLE ref_food_measures DROP COLUMN criterion_key;
DROP INDEX idx_ref_food_measures_global ON ref_food_measures;

-- The BEDCA columns go.
ALTER TABLE ref_food_measures DROP FOREIGN KEY fk_ref_food_measures_bedca;
DROP INDEX idx_ref_food_measures_bedca ON ref_food_measures;
ALTER TABLE ref_food_measures DROP COLUMN bedca_food_id;

ALTER TABLE ref_rations DROP FOREIGN KEY fk_ref_rations_bedca;
ALTER TABLE ref_rations DROP COLUMN bedca_food_id;

-- V14's rules again, on the composition food: one global criterion per food,
-- measure and size, and a global criterion always names a food and never a
-- source or a code.
ALTER TABLE ref_food_measures
    ADD COLUMN criterion_key VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN global_criterion
        THEN CONCAT(composition_food_id, ':', measure, ':', COALESCE(size, '')) END) STORED;

ALTER TABLE ref_food_measures
    ADD CONSTRAINT uk_ref_food_measures_criterion UNIQUE (criterion_key);

ALTER TABLE ref_food_measures
    ADD CONSTRAINT ck_ref_food_measures_global
        CHECK (NOT global_criterion
            OR (source_id IS NULL AND code IS NULL AND composition_food_id IS NOT NULL));

CREATE INDEX idx_ref_food_measures_global ON ref_food_measures (global_criterion, composition_food_id);

DROP TEMPORARY TABLE v16_food_map;
DROP TEMPORARY TABLE v16_bedca_composition;
