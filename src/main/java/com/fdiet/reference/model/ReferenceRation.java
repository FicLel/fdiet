package com.fdiet.reference.model;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.RationRole;
import com.fdiet.reference.domain.WeightBasis;
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
 * One standard serving as a guideline defines it, for one population.
 *
 * <p>It reaches the foods it covers either by naming one composition-database
 * food ({@code bedcaFoodId}), or by a food family narrowed by keywords — the
 * family is read off the food's name by {@code IFoodCategoriser}, the same way
 * {@code com.fdiet.alternative} reads it, so the two never disagree about what
 * a food is.
 */
@Entity
@Table(name = "ref_rations")
@Getter
@Setter
public class ReferenceRation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 80, nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "population_id", nullable = false)
    private ReferencePopulation population;

    @Column(name = "group_code", length = 40, nullable = false)
    private String groupCode;

    @Column(name = "group_label", length = 160, nullable = false)
    private String groupLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_category", length = 24)
    private FoodCategory foodCategory;

    @Column(name = "keywords", length = 500)
    private String keywords;

    /** Plain id rather than an association: the figures are never read through it. */
    @Column(name = "bedca_food_id")
    private Long bedcaFoodId;

    @Column(name = "food_label", length = 160)
    private String foodLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 24)
    private RationRole role;

    @Column(name = "grams_min", precision = 8, scale = 2)
    private BigDecimal gramsMin;

    @Column(name = "grams_max", precision = 8, scale = 2)
    private BigDecimal gramsMax;

    @Column(name = "ml_min", precision = 8, scale = 2)
    private BigDecimal mlMin;

    @Column(name = "ml_max", precision = 8, scale = 2)
    private BigDecimal mlMax;

    @Column(name = "units_min", precision = 6, scale = 2)
    private BigDecimal unitsMin;

    @Column(name = "units_max", precision = 6, scale = 2)
    private BigDecimal unitsMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private FoodState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_basis", length = 16, nullable = false)
    private WeightBasis weightBasis;

    @Column(name = "household_text", length = 255)
    private String householdText;

    @Column(name = "gross_grams", precision = 8, scale = 2)
    private BigDecimal grossGrams;

    @Column(name = "page_ref", length = 160, nullable = false)
    private String pageRef;

    @Column(name = "note", length = 500)
    private String note;

    /**
     * The smallest weight one ration is, in grams — millilitres read as grams,
     * the one assumption {@code PortionScaler} already makes — or null when the
     * ration is a count with no weight.
     */
    public BigDecimal weightMin() {
        return gramsMin != null ? gramsMin : mlMin;
    }

    public BigDecimal weightMax() {
        return gramsMax != null ? gramsMax : mlMax;
    }
}
