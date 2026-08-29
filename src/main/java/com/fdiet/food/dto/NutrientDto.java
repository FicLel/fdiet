package com.fdiet.food.dto;

import java.math.BigDecimal;

/**
 * A published nutrient figure as it left the source: the number and its own
 * unit, unrounded and unconverted.
 *
 * <p>Never read the value without the unit — energy is kJ for most foods and
 * kcal for a few. {@link NutritionDto} is the converted shape to do arithmetic
 * on; this one is the record of what was published.
 */
public record NutrientDto(BigDecimal value, String unit) {
}
