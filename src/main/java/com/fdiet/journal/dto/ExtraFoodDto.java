package com.fdiet.journal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.UnitWording;
import com.fdiet.reference.dto.FoodMeasureDto;

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
 * matched, or when the unit is one nothing can weigh. {@code measure} is the
 * household measure that weighed it, with its source, when one did.
 * {@code unitWording} is the unit in both numbers with its size agreeing — the
 * same shape the week's ingredients carry; see {@link UnitWording}.
 */
public record ExtraFoodDto(
        Long id,
        DayOfWeek day,
        String name,
        BigDecimal quantity,
        String unit,
        FoodState state,
        PortionSize size,
        Long bedcaFoodId,
        Long foodItemId,
        String matchedName,
        /** The maker, when the match came from the branded catalogue. */
        String brand,
        Long foodMeasureId,
        FoodMeasureDto measure,
        NutritionDto nutrition,
        LocalDateTime loggedAt) {

    /** The unit as the patient reads it, in both numbers; derived, never stored. */
    @JsonProperty("unitWording")
    public UnitWording unitWording() {
        return UnitWording.of(name, unit, size);
    }
}
