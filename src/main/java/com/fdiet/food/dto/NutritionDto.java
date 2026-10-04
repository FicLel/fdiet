package com.fdiet.food.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Composition figures converted to one set of units, so they can be scaled to a
 * portion and added up: energy in kcal, the macronutrients in grams, sodium in
 * milligrams.
 *
 * <p>This is a derived shape. The stored figures keep the units they were
 * published in — see {@link NutrientDto} and each source's attribution
 * ({@link com.fdiet.food.model.CompositionSource}) — and the
 * conversion happens on the way out, never on the way in.
 *
 * <p>A null field means the source published nothing for that component, which
 * is not the same as zero and must not be added as zero. Adding two nulls
 * leaves null, so a total is blank exactly when nothing contributed to it.
 */
public record NutritionDto(
        BigDecimal energyKcal,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal saturatedFatG,
        BigDecimal carbohydratesG,
        BigDecimal sugarsG,
        BigDecimal fiberG,
        BigDecimal sodiumMg) {

    /** Two decimals is as precise as a portion figure can honestly be. */
    private static final int SCALE = 2;

    public static final NutritionDto EMPTY =
            new NutritionDto(null, null, null, null, null, null, null, null);

    /**
     * True when nothing at all is known, so a caller can skip it entirely.
     * Not part of the JSON: Jackson would otherwise publish it as a field of
     * its own, and the eight figures already say it.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return energyKcal == null && proteinG == null && fatG == null && saturatedFatG == null
                && carbohydratesG == null && sugarsG == null && fiberG == null && sodiumMg == null;
    }

    /** These figures for {@code factor} times 100 g. */
    public NutritionDto scaled(BigDecimal factor) {
        return new NutritionDto(
                times(energyKcal, factor),
                times(proteinG, factor),
                times(fatG, factor),
                times(saturatedFatG, factor),
                times(carbohydratesG, factor),
                times(sugarsG, factor),
                times(fiberG, factor),
                times(sodiumMg, factor));
    }

    /** Component by component, where a component nobody knows stays unknown. */
    public NutritionDto plus(NutritionDto other) {
        if (other == null) {
            return this;
        }
        return new NutritionDto(
                sum(energyKcal, other.energyKcal),
                sum(proteinG, other.proteinG),
                sum(fatG, other.fatG),
                sum(saturatedFatG, other.saturatedFatG),
                sum(carbohydratesG, other.carbohydratesG),
                sum(sugarsG, other.sugarsG),
                sum(fiberG, other.fiberG),
                sum(sodiumMg, other.sodiumMg));
    }

    private static BigDecimal times(BigDecimal value, BigDecimal factor) {
        return value == null ? null : value.multiply(factor).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a.add(b);
    }
}
