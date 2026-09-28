package com.fdiet.reference.dto;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.RationRole;
import com.fdiet.reference.domain.WeightBasis;

import java.math.BigDecimal;

/**
 * One standard serving, exactly as its source defines it. A range is sent as a
 * range: nothing here picks a point inside one.
 */
public record RationDto(
        Long id,
        String code,
        String profileCode,
        String profileLabel,
        String sourceCode,
        String sourceShortName,
        String groupCode,
        String groupLabel,
        FoodCategory foodCategory,
        String keywords,
        String foodLabel,
        Long bedcaFoodId,
        RationRole role,
        BigDecimal gramsMin,
        BigDecimal gramsMax,
        BigDecimal mlMin,
        BigDecimal mlMax,
        BigDecimal unitsMin,
        BigDecimal unitsMax,
        FoodState state,
        WeightBasis weightBasis,
        String householdText,
        BigDecimal grossGrams,
        String pageRef,
        String note) {

    /** The smallest weight of one ration, millilitres read as grams; null for a bare count. */
    public BigDecimal weightMin() {
        return gramsMin != null ? gramsMin : mlMin;
    }

    public BigDecimal weightMax() {
        return gramsMax != null ? gramsMax : mlMax;
    }
}
