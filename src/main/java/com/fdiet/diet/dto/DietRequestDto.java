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
 */
public record DietRequestDto(
        @NotBlank String name,
        @NotNull LocalDate startedOn,
        @NotNull List<@NotNull @Valid DietDay> days) {
}
