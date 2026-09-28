package com.fdiet.diet.dto;

import com.fdiet.reference.domain.FoodState;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * A food to add to a cell by ration or by household measure, rather than by
 * typing it.
 *
 * <p>Exactly one of {@code grams} and {@code foodMeasureId}: an explicit weight
 * (a value the nutritionist picked inside a ration's range), or a count of a
 * household measure ("1 cucharada sopera"). {@code state} is written into the
 * text when given ("70 g en seco").
 *
 * @param dietId the diet the cell belongs to, when there is one, so its own
 *               measures and its profile weigh the result as they will when
 *               the week is published
 */
public record ComposeRequestDto(
        @NotNull Long bedcaFoodId,
        @Positive BigDecimal grams,
        Long foodMeasureId,
        @Positive BigDecimal count,
        FoodState state,
        Long dietId) {
}
