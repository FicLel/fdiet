package com.fdiet.food.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One generic food of the Spanish food composition database.
 *
 * <p>{@code nutrients} is keyed by the names in
 * {@link com.fdiet.food.model.Nutrient#key()} — {@code energy}, {@code protein},
 * {@code fat} … — and each entry carries the figure exactly as published, with
 * its unit. A component the source published nothing for is absent rather than
 * zero: no data and none of it are different answers.
 *
 * <p>{@code nutrition} is the same figures converted to one set of units so
 * they can be added up. It is derived on the way out and never stored.
 */
public record BedcaFoodDto(
        Long id,
        String name,
        String englishName,
        String scientificName,
        String foodGroup,
        String foodSubgroup,
        String origin,
        BigDecimal ediblePortion,
        Map<String, NutrientDto> nutrients,
        NutritionDto nutrition) {
}
