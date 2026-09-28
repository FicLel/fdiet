package com.fdiet.reference.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * The share of a day's energy one meal slot should carry, for one population.
 * The slot is the diet's own name for it ({@code BREAKFAST} … {@code DINNER}),
 * kept as text so this module does not reach into the diet's types.
 */
@Entity
@Table(name = "ref_meal_shares")
@Getter
@Setter
public class ReferenceMealShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 80, nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "population_id", nullable = false)
    private ReferencePopulation population;

    @Column(name = "meal_type", length = 24, nullable = false)
    private String mealType;

    @Column(name = "pct_min", precision = 5, scale = 2, nullable = false)
    private BigDecimal pctMin;

    @Column(name = "pct_max", precision = 5, scale = 2, nullable = false)
    private BigDecimal pctMax;

    @Column(name = "page_ref", length = 160, nullable = false)
    private String pageRef;

    @Column(name = "note", length = 500)
    private String note;
}
