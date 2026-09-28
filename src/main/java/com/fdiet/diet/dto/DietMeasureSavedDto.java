package com.fdiet.diet.dto;

import com.fdiet.reference.dto.FoodMeasureDto;

/**
 * A diet's own weight for a household measure, and how many of the diet's
 * stored ingredients it now weighs.
 */
public record DietMeasureSavedDto(
        FoodMeasureDto measure,
        int attached) {
}
