package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One meal of a {@link DietDay}. The {@code type} is its slot in the day, {@code name} a free
 * label. Suffixed to leave the bare name to {@link com.fdiet.diet.domain.Meal}, which owns a whole
 * day's worth of these.
 *
 * <p>{@code nutrition} is what the meal adds up to. It is filled on the way out
 * and ignored on the way in.
 */
public record MealDto(
        @NotNull MealType type,
        @NotBlank String name,
        @NotNull List<@NotNull @Valid Dish> dishes,
        NutritionSummaryDto nutrition) {

    /** A meal as it is written: the totals are worked out when it is read back. */
    public MealDto(MealType type, String name, List<Dish> dishes) {
        this(type, name, dishes, null);
    }
}
