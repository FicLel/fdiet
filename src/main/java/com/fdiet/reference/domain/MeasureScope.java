package com.fdiet.reference.domain;

/**
 * What a measure criterion reaches once it is written (FD-054): the recipe
 * ingredients and logged extras matched to its composition food and written in
 * its household measure — in one diet for that diet's criterion, in every diet
 * (and the library recipes) for a global one.
 *
 * @param dietId the diet whose criterion was written, or null for a global criterion
 */
public record MeasureScope(Long compositionFoodId, HouseholdMeasure measure, Long dietId) {

    /** Whether the criterion belongs to no diet, and so reaches every one. */
    public boolean global() {
        return dietId == null;
    }

    /** Whether a row written in {@code unit} is one the criterion's measure could weigh. */
    public boolean measures(String unit) {
        return HouseholdMeasure.ofUnit(unit).filter(written -> written == measure).isPresent();
    }
}
