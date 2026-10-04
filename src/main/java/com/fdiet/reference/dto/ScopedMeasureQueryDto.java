package com.fdiet.reference.dto;

/**
 * A measure question asked inside one diet: the diet whose own criteria apply
 * (null for a library recipe, which no diet's criterion weighs) and the profile
 * whose source settles a disagreement between published rows. Lets the rows of
 * many diets be answered in one batch.
 */
public record ScopedMeasureQueryDto(
        MeasureQueryDto query,
        Long dietId,
        String profileCode) {
}
