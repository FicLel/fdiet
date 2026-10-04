package com.fdiet.food.dto;

import java.math.BigDecimal;

/**
 * A published nutrient figure as it left the source: the number and its own
 * unit, unrounded and unconverted.
 *
 * <p>Never read the value without the unit — energy comes in kcal, sodium and
 * iron in mg, most of the rest in g. {@link NutritionDto} is the converted shape to do arithmetic
 * on; this one is the record of what was published.
 */
public record NutrientDto(BigDecimal value, String unit) {
}
