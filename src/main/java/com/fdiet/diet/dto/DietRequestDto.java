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
 */
public record DietRequestDto(
        @NotNull Long patientId,
        @NotBlank String name,
        @NotNull LocalDate startedOn,
        @NotNull List<@NotNull @Valid DietDay> days) {
}
