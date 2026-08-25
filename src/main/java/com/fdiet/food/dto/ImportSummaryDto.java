package com.fdiet.food.dto;

/** Outcome of a fooddata.csv import. */
public record ImportSummaryDto(
        int rowsRead,
        int rowsSkipped,
        int categoriesStored,
        int subCategoriesStored,
        int foodItemsInserted,
        int foodItemsAlreadyPresent
) {
}
