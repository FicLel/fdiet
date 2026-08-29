package com.fdiet.diet.dto;

import com.fdiet.diet.model.DietStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * A stored diet with its whole week, ordered by day and by meal slot.
 *
 * <p>The status is the plain enum rather than the entity it lives beside — the
 * entity itself never crosses this boundary.
 *
 * <p>{@code nutrition} is the whole week's total, and it carries the count of
 * ingredients nobody has matched yet: right after an import that count is most
 * of them, and the totals have to be read in its light.
 */
public record DietDto(
        Long id,
        String name,
        DietStatus status,
        LocalDate startedOn,
        LocalDate endedOn,
        List<DietDay> days,
        NutritionSummaryDto nutrition) {
}
