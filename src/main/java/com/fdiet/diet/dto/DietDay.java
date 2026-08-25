package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.util.List;

/** One day of the diet. The meals may be empty while the user is still filling the day in. */
public record DietDay(
        @NotNull DayOfWeek day,
        @NotNull List<@NotNull @Valid MealDto> meals) {
}
