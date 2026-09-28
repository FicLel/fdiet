package com.fdiet.journal.dto;

import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.PortionSize;
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
 * <p>{@code unit} is free text and keeps its own scale, as the week's do. A
 * household measure ("cucharada", "pieza") is weighed the way the week weighs
 * one: through {@code foodMeasureId} when given — one of the rows
 * {@code GET /api/reference/measures} offers for this food and unit — or through
 * the one row the rule can attach without a judgement. A quantity nothing can
 * weigh is stored as written and simply has no figures. {@code state} and
 * {@code size} are optional and only help choose that row.
 */
public record LogExtraFoodRequestDto(
        @NotNull DayOfWeek day,
        @NotBlank String name,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String unit,
        Long bedcaFoodId,
        Long foodItemId,
        FoodState state,
        PortionSize size,
        Long foodMeasureId) {

    /** An entry with nothing said about measures, the shape it had before they existed. */
    public LogExtraFoodRequestDto(DayOfWeek day, String name, BigDecimal quantity, String unit,
                                  Long bedcaFoodId, Long foodItemId) {
        this(day, name, quantity, unit, bedcaFoodId, foodItemId, null, null, null);
    }
}
