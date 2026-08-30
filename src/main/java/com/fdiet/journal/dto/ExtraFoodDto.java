package com.fdiet.journal.dto;

import com.fdiet.food.dto.NutritionDto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * One logged extra on the way out.
 *
 * <p>{@code name} is what the patient wrote; {@code matchedName} is what the
 * catalogue calls the food it was matched to, null while it is matched to
 * nothing. {@code nutrition} is the catalogue's per-100 g figures scaled to the
 * quantity logged, worked out on read and never stored — null when nothing was
 * matched, or when the unit is one nothing can weigh.
 */
public record ExtraFoodDto(
        Long id,
        DayOfWeek day,
        String name,
        BigDecimal quantity,
        String unit,
        Long bedcaFoodId,
        Long foodItemId,
        String matchedName,
        /** The maker, when the match came from the branded catalogue. */
        String brand,
        NutritionDto nutrition,
        LocalDateTime loggedAt) {
}
