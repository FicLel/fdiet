package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One meal of a {@link DietDay}. The {@code type} is its slot in the day, {@code name} a free
 * label. Suffixed to leave the bare name to {@link com.fdiet.diet.domain.Meal}, which owns a whole
 * day's worth of these.
 */
public record MealDto(
        @NotNull MealType type,
        @NotBlank String name,
        @NotNull List<@NotNull @Valid Dish> dishes) {
}
