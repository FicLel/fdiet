-- Patients, and the diet each of them is written for.
--
-- THIS IS STILL NOT A SECURITY LAYER. A patient is a name a diet belongs to,
-- not an account: nothing authenticates, nothing is hidden, and any patient's
-- week is readable through the same endpoints as any other's. That is the
-- current stage of the project, stated here rather than implied. When accounts
-- arrive, this table is what they attach to.
--
-- The single-active-diet rule becomes a per-patient rule, and it moves entirely
-- into the index. `diets.active_flag` is unchanged — 1 while the row is ACTIVE,
-- NULL once it is archived — but `uk_diets_active` now spans
-- (patient_id, active_flag). MySQL lets a unique index hold any number of rows
-- with a NULL in them, so every patient keeps all of their archived diets and
-- at most one active one, and no two patients contend for the same slot.

CREATE TABLE patients (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    name       VARCHAR(255)  NOT NULL,
    -- The nutritionist's own note about the person. Free text, never parsed.
    notes      VARCHAR(1000) NULL,
    created_at DATETIME      NOT NULL,
    PRIMARY KEY (id),
    -- While there are no accounts the name is the whole identity, so two
    -- patients may not share one: a list of two `Victor`s names nobody. The
    -- utf8mb4_unicode_ci collation makes that comparison case- and
    -- accent-insensitive, which is how a person reading the list would compare.
    CONSTRAINT uk_patients_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Every diet already stored was written for one person. This is who, and the
-- row is seeded on an empty database too: a screen that picks a patient before
-- it can do anything needs one to exist before the first diet does.
INSERT INTO patients (name, created_at) VALUES ('Victor', NOW());

ALTER TABLE diets ADD COLUMN patient_id BIGINT NULL AFTER id;

UPDATE diets SET patient_id = (SELECT id FROM patients WHERE name = 'Victor');

ALTER TABLE diets MODIFY COLUMN patient_id BIGINT NOT NULL;

-- One active diet in the whole table becomes one active diet per patient.
ALTER TABLE diets DROP INDEX uk_diets_active;
ALTER TABLE diets ADD CONSTRAINT uk_diets_active UNIQUE (patient_id, active_flag);

-- A patient a diet is written for must not be deletable underneath it, so this
-- foreign key does not cascade: the diets are the record of the person.
ALTER TABLE diets
    ADD CONSTRAINT fk_diets_patient FOREIGN KEY (patient_id) REFERENCES patients (id);

-- The history listing is one patient's diets, newest first.
CREATE INDEX idx_diets_patient_started ON diets (patient_id, started_on);
