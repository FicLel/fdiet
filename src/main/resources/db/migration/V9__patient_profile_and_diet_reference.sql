-- Who a diet is written for, a little more precisely, and what it is read against.
--
-- A patient is still a name first: both new columns are optional. The birth date
-- is what lets the editor suggest the right ration profile (a 9-year-old is not
-- read against adult rations), and sex is kept because several reference values
-- are published per sex. There is still no security layer, and every patient's
-- row is readable by anyone who opens the app; that was accepted for these two
-- fields explicitly.

ALTER TABLE patients
    ADD COLUMN birth_date DATE       NULL AFTER notes,
    ADD COLUMN sex        VARCHAR(8) NULL AFTER birth_date,
    ADD CONSTRAINT ck_patients_sex CHECK (sex IN ('FEMALE', 'MALE'));

-- The reference profile a week is written against: a choice the nutritionist
-- makes, stored like a food match and never changed silently. No foreign key —
-- the reference tables are loaded from CSV after the schema exists, and a diet
-- must not become unwritable because a sync has not run yet; the service checks
-- the code instead.
--
-- `clinical` marks a diet written for a clinical situation. Clinical exchange
-- systems (diabetes carbohydrate rations) are only offered on one of these.
ALTER TABLE diets
    ADD COLUMN reference_profile_code VARCHAR(64) NULL AFTER name,
    ADD COLUMN clinical               BOOLEAN     NOT NULL DEFAULT FALSE AFTER reference_profile_code;
