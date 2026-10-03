package com.fdiet.reference.dto;

/**
 * What a change to a measure would reach: the recipe ingredients and logged
 * extras weighed by it now. A change is live for all of them — they point at the
 * row — the way an edit to a library recipe is live on every plate.
 */
public record MeasureUsageDto(
        Long measureId,
        long ingredients,
        long extraFoods) {

    public boolean inUse() {
        return ingredients > 0 || extraFoods > 0;
    }
}
