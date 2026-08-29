package com.fdiet.food.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One row of bedca_foods.csv, typed but not yet stored. The importer builds
 * these and hands them to the service that owns the table, the way
 * {@link FoodCsvRowDto} works for the branded catalogue.
 */
public record BedcaCsvRowDto(
        Long id,
        String name,
        String englishName,
        String scientificName,
        String foodGroup,
        String foodSubgroup,
        String origin,
        BigDecimal ediblePortion,
        Map<String, NutrientDto> nutrients) {
}
