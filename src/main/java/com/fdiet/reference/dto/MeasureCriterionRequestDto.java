package com.fdiet.reference.dto;

import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A nutritionist's own weight for one household measure of one food — for one
 * diet ("en esta dieta, 1 cucharadita de AOVE son 5 ml") or, through
 * {@code /api/reference/criteria}, for every diet ("1 huevo mediano son 58 g").
 *
 * <p>Exactly one of {@code grams} and {@code ml}. It weighs one measure of one
 * composition-database food; a size narrows it to the pieces written with that
 * size. The weight is what one measure puts on the plate, the edible part.
 */
public record MeasureCriterionRequestDto(
        @NotNull HouseholdMeasure measure,
        PortionSize size,
        @NotNull Long bedcaFoodId,
        @Positive BigDecimal grams,
        @Positive BigDecimal ml,
        @Size(max = 500) String note) {
}
