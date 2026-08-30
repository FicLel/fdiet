package com.fdiet.journal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.DayOfWeek;

/**
 * Something eaten off the plan.
 *
 * <p>The two catalogue ids are optional and at most one may be given — matching
 * to both would leave the entry pointing at two different foods. Neither is an
 * entry that counts towards nothing and is kept anyway, which is the honest
 * answer for "un trozo de tarta" that no catalogue carries.
 *
 * <p>{@code unit} is free text and keeps its own scale, as the week's do: a
 * quantity nothing can weigh ("1 unidad") is stored as written and simply has
 * no figures.
 */
public record LogExtraFoodRequestDto(
        @NotNull DayOfWeek day,
        @NotBlank String name,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String unit,
        Long bedcaFoodId,
        Long foodItemId) {
}
