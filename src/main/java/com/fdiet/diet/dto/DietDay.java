package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.util.List;

/**
 * One day of the diet. The meals may be empty while the user is still filling the day in.
 *
 * <p>{@code nutrition} is the day's total, filled on the way out and ignored on
 * the way in.
 */
public record DietDay(
        @NotNull DayOfWeek day,
        @NotNull List<@NotNull @Valid MealDto> meals,
        NutritionSummaryDto nutrition) {

    /** A day as it is written: the totals are worked out when it is read back. */
    public DietDay(DayOfWeek day, List<MealDto> meals) {
        this(day, meals, null);
    }
}
