-- The nutrition reference layer: what published guidelines call a ration, how
-- a household measure is weighed, how often a group should be eaten, and how a
-- day's energy is shared between its meals.
--
-- WHY THESE ARE TABLES AND NOT CONSTANTS.
-- Every figure here comes from a document, and the documents disagree: one
-- cucharada sopera of olive oil is 10 ml for AESAN 2022 and 15 ml for SENC 2018;
-- a ration of legumes is 50-60 g dry for one and 60-80 g for the other. Nothing
-- is averaged and nothing is picked silently. Every row names its source, the
-- page it was read from, the raw/cooked state and the net/gross basis the source
-- wrote, and the population band exactly as the source labelled it. A diet is
-- read against one profile (a source's population band) at a time.
--
-- The rows are curated CSVs under reference-data/, loaded by
-- POST /api/reference/sync. Nothing derived from them — a ration count, grams
-- resolved from a measure, an exchange count — is ever stored.

CREATE TABLE ref_sources (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    -- Stable key the CSVs refer to, e.g. AESAN-2022-007.
    code          VARCHAR(64)   NOT NULL,
    -- What a chip beside a figure says: "AESAN 2022".
    short_name    VARCHAR(80)   NOT NULL,
    title         VARCHAR(500)  NOT NULL,
    institution   VARCHAR(255)  NOT NULL,
    country       VARCHAR(8)    NOT NULL,
    -- 1 Spain, 2 Europe, 3 UK/US/international, 4 secondary.
    tier          INT           NOT NULL,
    year          INT           NULL,
    url           VARCHAR(500)  NULL,
    -- A reusable, B with attribution, C permission needed, D cite only, E unknown.
    licence_class VARCHAR(1)    NOT NULL,
    licence       VARCHAR(1000) NOT NULL,
    -- Shown wherever a figure from this source is shown.
    attribution   VARCHAR(1000) NOT NULL,
    clinical      BOOLEAN       NOT NULL,
    retrieved_on  DATE          NOT NULL,
    notes         VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ref_sources_code UNIQUE (code),
    CONSTRAINT ck_ref_sources_licence CHECK (licence_class IN ('A', 'B', 'C', 'D', 'E'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- A population band of one source, labelled the way the source labelled it.
-- "3-6 años" and "Etapa juvenil" are not normalised into one scale: the ages are
-- filled only where the source states them. A selectable band is a profile a
-- diet can be written against.
CREATE TABLE ref_populations (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    code                 VARCHAR(64)  NOT NULL,
    source_id            BIGINT       NOT NULL,
    label                VARCHAR(160) NOT NULL,
    age_min_months       INT          NULL,
    age_max_months       INT          NULL,
    context              VARCHAR(255) NULL,
    -- Where the meal energy shares for this band come from, when this band's own
    -- source has none; the note says so wherever the shares are drawn.
    meal_shares_from     VARCHAR(64)  NULL,
    meal_shares_note     VARCHAR(500) NULL,
    selectable           BOOLEAN      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ref_populations_code UNIQUE (code),
    CONSTRAINT fk_ref_populations_source FOREIGN KEY (source_id) REFERENCES ref_sources (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- A ration as a guideline defines it: a weight (or volume, or count) that is one
-- standard serving of a group or of one food, for one population.
--
-- `group_code` ties rations to the recommendations that count them (AESAN's
-- "Cereales" is counted over both its bread and its pasta/rice rows). A row
-- reaches the foods it covers through fdiet's food family (`food_category`,
-- read off the BEDCA name) narrowed by `keywords`, or names one BEDCA food
-- outright. Ranges stay ranges.
CREATE TABLE ref_rations (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    code           VARCHAR(80)   NOT NULL,
    population_id  BIGINT        NOT NULL,
    group_code     VARCHAR(40)   NOT NULL,
    group_label    VARCHAR(160)  NOT NULL,
    food_category  VARCHAR(24)   NULL,
    keywords       VARCHAR(500)  NULL,
    bedca_food_id  BIGINT        NULL,
    food_label     VARCHAR(160)  NULL,
    role           VARCHAR(24)   NULL,
    grams_min      DECIMAL(8, 2) NULL,
    grams_max      DECIMAL(8, 2) NULL,
    ml_min         DECIMAL(8, 2) NULL,
    ml_max         DECIMAL(8, 2) NULL,
    units_min      DECIMAL(6, 2) NULL,
    units_max      DECIMAL(6, 2) NULL,
    state          VARCHAR(16)   NOT NULL,
    weight_basis   VARCHAR(16)   NOT NULL,
    household_text VARCHAR(255)  NULL,
    gross_grams    DECIMAL(8, 2) NULL,
    page_ref       VARCHAR(160)  NOT NULL,
    note           VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ref_rations_code UNIQUE (code),
    CONSTRAINT fk_ref_rations_population
        FOREIGN KEY (population_id) REFERENCES ref_populations (id),
    CONSTRAINT fk_ref_rations_bedca FOREIGN KEY (bedca_food_id) REFERENCES bedca_foods (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_ref_rations_population ON ref_rations (population_id);

-- A household measure of a food, in grams or millilitres: "1 Ud. mediana" of
-- kiwi is 80 g net. `measure_count` keeps the published figure verbatim — "3 Uds.
-- medianas" of apricot is 180 g — and one unit is divided out on read.
--
-- A row is either published (it has a source) or one nutritionist's decision
-- for one diet (it has a diet), never both: "my patients' cucharadita of AOVE is
-- 5 ml" belongs to that diet and is shown as the professional's criterion, never
-- mixed in with a published figure. MySQL refuses a CHECK on a column a
-- cascading foreign key acts on, so the either-or is the service's rule.
CREATE TABLE ref_food_measures (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    code           VARCHAR(80)   NULL,
    source_id      BIGINT        NULL,
    diet_id        BIGINT        NULL,
    measure        VARCHAR(32)   NOT NULL,
    size           VARCHAR(8)    NULL,
    measure_count  DECIMAL(6, 2) NOT NULL,
    bedca_food_id  BIGINT        NULL,
    food_category  VARCHAR(24)   NULL,
    keywords       VARCHAR(500)  NULL,
    food_label     VARCHAR(160)  NOT NULL,
    grams_min      DECIMAL(8, 2) NULL,
    grams_max      DECIMAL(8, 2) NULL,
    ml_min         DECIMAL(8, 2) NULL,
    ml_max         DECIMAL(8, 2) NULL,
    state          VARCHAR(16)   NOT NULL,
    weight_basis   VARCHAR(16)   NOT NULL,
    gross_grams    DECIMAL(8, 2) NULL,
    household_text VARCHAR(255)  NULL,
    page_ref       VARCHAR(160)  NULL,
    note           VARCHAR(500)  NULL,
    created_at     DATETIME      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ref_food_measures_code UNIQUE (code),
    CONSTRAINT ck_ref_food_measures_count CHECK (measure_count > 0),
    CONSTRAINT fk_ref_food_measures_source FOREIGN KEY (source_id) REFERENCES ref_sources (id),
    -- A diet's own measures go with the diet.
    CONSTRAINT fk_ref_food_measures_diet
        FOREIGN KEY (diet_id) REFERENCES diets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ref_food_measures_bedca FOREIGN KEY (bedca_food_id) REFERENCES bedca_foods (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_ref_food_measures_diet ON ref_food_measures (diet_id);
CREATE INDEX idx_ref_food_measures_bedca ON ref_food_measures (bedca_food_id);

-- How many rations of which groups, how often, for whom. A row with only a
-- maximum is a ceiling ("carne: máximo 3 raciones a la semana") and is drawn as
-- one; `group_codes` is a semicolon list because a recommendation can count
-- several ration groups together ("5 raciones de hortalizas y frutas").
CREATE TABLE ref_recommendations (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    code          VARCHAR(80)   NOT NULL,
    population_id BIGINT        NOT NULL,
    label         VARCHAR(160)  NOT NULL,
    group_codes   VARCHAR(160)  NOT NULL,
    rations_min   DECIMAL(6, 2) NULL,
    rations_max   DECIMAL(6, 2) NULL,
    period        VARCHAR(16)   NOT NULL,
    page_ref      VARCHAR(160)  NOT NULL,
    note          VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ref_recommendations_code UNIQUE (code),
    CONSTRAINT ck_ref_recommendations_bound
        CHECK (rations_min IS NOT NULL OR rations_max IS NOT NULL),
    CONSTRAINT fk_ref_recommendations_population
        FOREIGN KEY (population_id) REFERENCES ref_populations (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The share of the day's energy a meal slot should carry. `meal_type` is the
-- diet's own slot name (BREAKFAST … DINNER).
CREATE TABLE ref_meal_shares (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    code          VARCHAR(80)   NOT NULL,
    population_id BIGINT        NOT NULL,
    meal_type     VARCHAR(24)   NOT NULL,
    pct_min       DECIMAL(5, 2) NOT NULL,
    pct_max       DECIMAL(5, 2) NOT NULL,
    page_ref      VARCHAR(160)  NOT NULL,
    note          VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ref_meal_shares_code UNIQUE (code),
    CONSTRAINT fk_ref_meal_shares_population
        FOREIGN KEY (population_id) REFERENCES ref_populations (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- An exchange system's definition only — "1 ración de HC = 10 g de hidratos de
-- carbono". No published exchange list is copied: the counts are worked out on
-- read from the composition figures. A clinical system is only offered on a
-- diet marked clinical.
CREATE TABLE ref_exchange_systems (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    code           VARCHAR(80)   NOT NULL,
    source_id      BIGINT        NOT NULL,
    name           VARCHAR(160)  NOT NULL,
    nutrient       VARCHAR(16)   NOT NULL,
    grams_per_unit DECIMAL(6, 2) NOT NULL,
    clinical       BOOLEAN       NOT NULL,
    note           VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ref_exchange_systems_code UNIQUE (code),
    CONSTRAINT fk_ref_exchange_systems_source FOREIGN KEY (source_id) REFERENCES ref_sources (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
