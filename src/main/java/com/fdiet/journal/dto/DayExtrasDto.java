package com.fdiet.journal.dto;

import com.fdiet.diet.dto.NutritionSummaryDto;

import java.time.DayOfWeek;
import java.util.List;

/**
 * One day's off-plan entries and what they add up to.
 *
 * <p>The summary is {@link NutritionSummaryDto}, the diet module's, rather than
 * a second record of the same five fields: an extra lands in {@code counted},
 * {@code unmatched} or {@code unmeasured} for exactly the reasons an
 * ingredient does, and a total that travels with its counts should mean the
 * same thing on both sides of the plan.
 */
public record DayExtrasDto(
        DayOfWeek day,
        List<ExtraFoodDto> extras,
        NutritionSummaryDto nutrition) {
}
