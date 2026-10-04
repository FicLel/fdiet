package com.fdiet.food.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One published nutrient figure: the number and the unit it was published in.
 *
 * <p>They travel together because each source publishes a unit per component —
 * kcal for energy, mg for sodium, g for protein — and the stored figure is kept
 * exactly as published, so the unit is part of the record. A value read without
 * its unit is a wrong number.
 *
 * <p>Hibernate hands back a null embeddable when both columns are null, which
 * is exactly "the source published nothing for this component".
 */
@Embeddable
@Getter
@Setter
public class NutrientValue {

    @Column(precision = 14, scale = 6)
    private BigDecimal value;

    @Column(length = 16)
    private String unit;

    protected NutrientValue() {
    }

    public NutrientValue(BigDecimal value, String unit) {
        this.value = value;
        this.unit = unit;
    }

    /** Null when the source published no figure, so nothing is ever invented. */
    public static NutrientValue of(BigDecimal value, String unit) {
        return value == null && unit == null ? null : new NutrientValue(value, unit);
    }
}
