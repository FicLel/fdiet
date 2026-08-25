package com.fdiet.food.dto;

import java.math.BigDecimal;

/**
 * One parsed line of fooddata.csv.
 *
 * <p>Produced by the import service and handed to the services that own the
 * matching tables, so no service ever reaches into another one's entities.
 */
public record FoodCsvRowDto(
        Long categoryId,
        String categoryName,
        Long subCategoryId,
        String subCategoryName,
        Integer year,
        String sourceName,
        String ean,
        String commercialName,
        String brand,
        String legalName,
        String ingredients,
        BigDecimal energyKj,
        BigDecimal energyKcal,
        BigDecimal fatG,
        BigDecimal saturatedFatG,
        BigDecimal carbohydratesG,
        BigDecimal sugarsG,
        BigDecimal proteinsG,
        BigDecimal saltG
) {
}
