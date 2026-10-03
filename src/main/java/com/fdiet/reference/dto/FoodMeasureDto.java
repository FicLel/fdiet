package com.fdiet.reference.dto;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.WeightBasis;

import java.math.BigDecimal;

/**
 * What a household measure of a food weighs, and who says so.
 *
 * <p>{@code count} and the grams are the figure as it was printed ("3 Uds.
 * medianas, 180 g"); {@code gramsPerMeasure} is one unit of it, divided out on
 * read, and null when the source gave a range — a range weighs nothing until a
 * person picks a value in it. {@code dietOwn} marks a nutritionist's criterion
 * for one diet and {@code globalOwn} her criterion for every diet; neither is
 * ever presented as a published figure, and their source fields are null.
 */
public record FoodMeasureDto(
        Long id,
        String code,
        HouseholdMeasure measure,
        String measureLabel,
        PortionSize size,
        BigDecimal count,
        String foodLabel,
        Long bedcaFoodId,
        FoodCategory foodCategory,
        String keywords,
        BigDecimal gramsMin,
        BigDecimal gramsMax,
        BigDecimal mlMin,
        BigDecimal mlMax,
        BigDecimal gramsPerMeasure,
        FoodState state,
        WeightBasis weightBasis,
        BigDecimal grossGrams,
        String householdText,
        String pageRef,
        String note,
        String sourceCode,
        String sourceShortName,
        Integer sourceTier,
        Long dietId,
        boolean dietOwn,
        boolean globalOwn) {

    /** Whether one measure can be turned into grams at all. */
    public boolean weighs() {
        return gramsPerMeasure != null;
    }
}
