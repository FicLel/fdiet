package com.fdiet.diet.dto;

/**
 * What an imported workbook turned into. {@code unresolved} is the count worth
 * looking at: those ingredients were stored but still have to be matched to a
 * catalogue product, which is what
 * {@code GET /api/diets/{id}/ingredients?resolved=false} lists.
 */
public record DietImportSummaryDto(
        Long dietId,
        String sheet,
        String name,
        int days,
        int meals,
        int dishes,
        int ingredients,
        int resolved,
        int unresolved) {
}
