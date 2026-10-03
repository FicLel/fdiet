package com.fdiet.food.dto;

import com.fdiet.food.model.CompositionSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One food of CIQUAL 2025 or BLS 4.0.
 *
 * <p>{@code source}, {@code sourceLabel} and {@code attribution} travel with
 * every food: the two tables measure protein and energy differently, so a figure
 * is only read correctly beside the table it came from, and both licences ask
 * for the attribution wherever the figures are shown.
 *
 * <p>{@code nutrients} carries the figures exactly as published, each with its
 * unit, keyed {@code energy}, {@code protein}, …; a component the source did not
 * publish a number for is absent. {@code nutrition} is the same figures
 * converted for arithmetic, derived on the way out. {@code energyPublished} is
 * false for the foods whose energy the source leaves blank (145 in CIQUAL) — a
 * figure nothing downstream may assume.
 *
 * <p>{@code nameEs}, {@code aliases}, {@code namePreferred}, {@code nameReviewed},
 * {@code ediblePortion} and {@code ediblePortionFdcId} are fdiet's crosswalk, not
 * the source's: null / empty on a food it does not name yet, and
 * {@code nameReviewed} false until a person approves the row.
 */
public record CompositionFoodDto(
        Long id,
        CompositionSource source,
        String sourceLabel,
        String attribution,
        String sourceCode,
        String nameOriginal,
        String nameEn,
        String foodGroupCode,
        String nameEs,
        List<String> aliases,
        boolean namePreferred,
        boolean nameReviewed,
        BigDecimal ediblePortion,
        Integer ediblePortionFdcId,
        boolean energyPublished,
        Map<String, NutrientDto> nutrients,
        NutritionDto nutrition) {
}
