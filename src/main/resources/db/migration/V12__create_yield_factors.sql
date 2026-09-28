-- How much a food weighs once cooked, per 100 g of it raw: a published cooking
-- yield (USDA 2014: "Chicken, broiler-fryer, breast, meat and skin, roasted,
-- 72 %").
--
-- A yield is only ever *offered*. An ingredient written "150 g en crudo" and
-- matched to a grilled food is a different amount of food than its figures say,
-- and a yield row says by how much — but which cut, which method and which
-- source a diet's chicken stands for is a judgement, so nothing converts a
-- quantity on its own. The row reaches the foods it covers the way a ration
-- does: fdiet's food family read off the BEDCA name, narrowed by `keywords`.
-- `method_keywords` are the cooking words the row's method is written as in
-- Spanish ("asado;horno"); a row whose method the text names is offered first.
--
-- `food_label` and `method` are the source's own words, verbatim; `page_ref`
-- names the row in the source table.

CREATE TABLE ref_yield_factors (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    code            VARCHAR(80)   NOT NULL,
    source_id       BIGINT        NOT NULL,
    food_category   VARCHAR(24)   NOT NULL,
    keywords        VARCHAR(500)  NULL,
    food_label      VARCHAR(255)  NOT NULL,
    method          VARCHAR(80)   NOT NULL,
    method_keywords VARCHAR(255)  NULL,
    yield_pct       DECIMAL(5, 1) NOT NULL,
    samples         INT           NULL,
    page_ref        VARCHAR(120)  NOT NULL,
    note            VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ref_yield_factors_code UNIQUE (code),
    CONSTRAINT fk_ref_yield_factors_source FOREIGN KEY (source_id) REFERENCES ref_sources (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
