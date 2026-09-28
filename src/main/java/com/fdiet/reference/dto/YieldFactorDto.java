package com.fdiet.reference.dto;

import com.fdiet.alternative.domain.FoodCategory;

import java.math.BigDecimal;

/**
 * A published cooking yield, exactly as its source gives it: 100 g of the food
 * raw weighs {@code yieldPct} g once cooked by {@code method}. {@code foodLabel}
 * and {@code method} are the source's own words. It is an offer — which cut and
 * which method a diet's food stands for is a person's call.
 */
public record YieldFactorDto(
        Long id,
        String code,
        String sourceCode,
        String sourceShortName,
        FoodCategory foodCategory,
        String keywords,
        String foodLabel,
        String method,
        String methodKeywords,
        BigDecimal yieldPct,
        Integer samples,
        String pageRef,
        String note) {
}
