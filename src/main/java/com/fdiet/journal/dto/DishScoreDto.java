package com.fdiet.journal.dto;

import com.fdiet.diet.dto.MealType;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * One plate's score, and the slot it belongs to.
 *
 * <p>The slot, not a dish id: a score outlives the republish that renumbers
 * every dish in the week. See {@link com.fdiet.journal.model.DishScore}.
 */
public record DishScoreDto(
        DayOfWeek day,
        MealType mealType,
        int dishIndex,
        int score,
        LocalDateTime scoredAt) {
}
