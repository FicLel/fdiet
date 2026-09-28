package com.fdiet.reference.dto;

import com.fdiet.reference.domain.ExchangeNutrient;

import java.math.BigDecimal;

/** An exchange unit's definition, and whether it is only for clinical diets. */
public record ExchangeSystemDto(
        String code,
        String name,
        ExchangeNutrient nutrient,
        BigDecimal gramsPerUnit,
        boolean clinical,
        String sourceCode,
        String sourceShortName,
        String note) {
}
