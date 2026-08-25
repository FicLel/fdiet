package com.fdiet.food.dto;

import java.math.BigDecimal;

/**
 * A food item as it crosses a layer boundary.
 *
 * <p>Fields the current fooddata.csv does not carry are still exposed so the
 * contract does not change once a richer export is imported.
 */
public record FoodItemDto(
        Long id,
        CategoryDto category,
        SubCategoryDto subCategory,
        Integer year,
        String sourceName,
        BigDecimal marketShareTotalEan,
        String ean,
        String commercialName,
        String manufacturer,
        String brand,
        String subbrand,
        String legalName,
        String ingredients,
        BigDecimal portionSizeG,
        BigDecimal energyKj,
        BigDecimal energyKcal,
        BigDecimal fatG,
        BigDecimal saturatedFatG,
        BigDecimal carbohydratesG,
        BigDecimal sugarsG,
        BigDecimal proteinsG,
        BigDecimal saltG,
        BigDecimal sodiumG,
        BigDecimal monounsaturatedFatG,
        BigDecimal polyunsaturatedFatG,
        BigDecimal starchG,
        BigDecimal fiberG,
        BigDecimal polyolsG,
        String sweeteners
) {
}
