package com.fdiet.reference.dto;

/**
 * A criterion as written, and what writing it re-weighed: the rows it reaches
 * whose measure nobody picked are chosen again at once, in every week it applies
 * to, archived ones included (FD-054).
 */
public record MeasureCriterionSavedDto(
        FoodMeasureDto measure,
        MeasureReweighDto reweighed) {
}
