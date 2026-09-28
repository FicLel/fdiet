package com.fdiet.diet.dto;

import com.fdiet.diet.model.DietStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * A stored diet with its whole week, ordered by day and by meal slot.
 *
 * <p>The status is the plain enum rather than the entity it lives beside — the
 * entity itself never crosses this boundary. {@code patientName} travels beside
 * {@code patientId} for the same reason an ingredient's {@code matchedName}
 * does: a screen showing whose week this is should not have to fetch a patient
 * to write a heading.
 *
 * <p>{@code nutrition} is the whole week's total, and it carries the count of
 * ingredients nobody has matched yet: right after an import that count is most
 * of them, and the totals have to be read in its light.
 *
 * <p>{@code referenceProfileCode} is the ration profile the week is written
 * against, null for none; {@code clinical} marks a diet written for a clinical
 * situation.
 */
public record DietDto(
        Long id,
        Long patientId,
        String patientName,
        String name,
        DietStatus status,
        LocalDate startedOn,
        LocalDate endedOn,
        String referenceProfileCode,
        boolean clinical,
        List<DietDay> days,
        NutritionSummaryDto nutrition) {
}
