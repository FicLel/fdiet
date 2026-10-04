package com.fdiet.reference.dto;

/**
 * How many rows a criterion's save weighed differently (FD-054): the recipe
 * ingredients and logged extras whose measure nobody picked and which the rule
 * now answers with another measure — the new criterion, usually, or none when
 * the criterion made the choice a judgement.
 */
public record MeasureReweighDto(
        long ingredients,
        long extraFoods) {

    public static final MeasureReweighDto NONE = new MeasureReweighDto(0, 0);

    public long total() {
        return ingredients + extraFoods;
    }
}
