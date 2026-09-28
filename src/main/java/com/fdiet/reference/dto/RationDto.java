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

    /**
     * One ration's weight as edible grams of a food, as a [min, max] pair, or
     * null when it cannot be compared: a bare count ("1 unidad"), or a gross
     * weight for a food whose edible fraction is not published. Composition
     * figures are per 100 g of what is eaten, so a gross ration is cut to that.
     *
     * @param ediblePortion the food's published edible fraction, or null
     */
    public BigDecimal[] edibleWeight(BigDecimal ediblePortion) {
        BigDecimal min = weightMin();
        BigDecimal max = weightMax();
        if (min == null || max == null || min.signum() <= 0 || max.signum() <= 0) {
            return null;
        }
        if (weightBasis == WeightBasis.GROSS) {
            if (ediblePortion == null || ediblePortion.signum() <= 0
                    || ediblePortion.compareTo(BigDecimal.ONE) > 0) {
                return null;
            }
            min = min.multiply(ediblePortion);
            max = max.multiply(ediblePortion);
        }
        return new BigDecimal[]{min, max};
    }
}
