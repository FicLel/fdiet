package com.fdiet.reference.model;

import com.fdiet.alternative.domain.FoodCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A published cooking yield: what 100 g of a food raw weighs once cooked by one
 * method. Offered beside an ingredient whose written state disagrees with the
 * food it was matched to, and never applied on its own.
 */
@Entity
@Table(name = "ref_yield_factors")
@Getter
@Setter
public class ReferenceYieldFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 80, nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ReferenceSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_category", length = 24, nullable = false)
    private FoodCategory foodCategory;

    @Column(name = "keywords", length = 500)
    private String keywords;

    /** The source's own description of the food, verbatim. */
    @Column(name = "food_label", length = 255, nullable = false)
    private String foodLabel;

    /** The source's own cooking method, verbatim. */
    @Column(name = "method", length = 80, nullable = false)
    private String method;

    /** The Spanish cooking words the method is written as, so a text naming it ranks it first. */
    @Column(name = "method_keywords", length = 255)
    private String methodKeywords;

    @Column(name = "yield_pct", precision = 5, scale = 1, nullable = false)
    private BigDecimal yieldPct;

    @Column(name = "samples")
    private Integer samples;

    @Column(name = "page_ref", length = 120, nullable = false)
    private String pageRef;

    @Column(name = "note", length = 500)
    private String note;
}
