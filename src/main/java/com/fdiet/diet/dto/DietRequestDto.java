package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * A week as it is submitted, whether written by hand or built from a workbook.
 * The days may be empty: the nutritionist fills the week in over several
 * sittings.
 *
 * <p>{@code patientId} says whose week it is and is required. On a replacement
 * it must be the patient the diet already belongs to — a diet does not change
 * hands through an edit; {@code POST /api/diets/{id}/copy} is how a week reaches
 * somebody else, and it leaves the original where it was.
 *
 * <p>{@code referenceProfileCode} is the ration profile the week is written
 * against. Left out on a new diet, the one suggested for the patient's age is
 * used (the adult profile when the age is unknown); left out on a replacement,
 * the diet keeps the one it has. A blank is "none". {@code clinical} follows the
 * same rule.
 */
public record DietRequestDto(
        @NotNull Long patientId,
        @NotBlank String name,
        @NotNull LocalDate startedOn,
        @NotNull List<@NotNull @Valid DietDay> days,
        String referenceProfileCode,
        Boolean clinical) {

    /** A week with no profile chosen: the default applies. */
    public DietRequestDto(Long patientId, String name, LocalDate startedOn, List<DietDay> days) {
        this(patientId, name, startedOn, days, null, null);
    }
}
