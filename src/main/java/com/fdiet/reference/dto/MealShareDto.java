package com.fdiet.reference.dto;

import java.math.BigDecimal;

/** The share of the day's energy a meal slot should carry, as a range. */
public record MealShareDto(
        String mealType,
        BigDecimal pctMin,
        BigDecimal pctMax,
        String note) {
}
