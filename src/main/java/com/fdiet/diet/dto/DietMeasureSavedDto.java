package com.fdiet.diet.dto;

import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureReweighDto;

/**
 * A diet's own weight for a household measure, and what saving it re-weighed:
 * the diet's ingredients and extras of that food and measure whose measure
 * nobody picked, chosen again by the rule (FD-054). {@code attached} is
 * {@code reweighed}'s total, the number this answer carried before the split.
 */
public record DietMeasureSavedDto(
        FoodMeasureDto measure,
        int attached,
        MeasureReweighDto reweighed) {
}
