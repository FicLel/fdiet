package com.fdiet.diet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fdiet.common.helper.RetiredFields;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;

/**
 * A match the editor already holds for an ingredient of the text it sends back to
 * be read again (FD-048): what the ingredient is called, and the food — and the
 * household measure, when one was picked — a person or the composer chose for it.
 *
 * <p>Exactly one of {@code compositionFoodId} and {@code foodItemId} names the
 * food; both or neither is a 400. {@code bedcaFoodId} is read only to be refused
 * (FD-033). {@code foodMeasureId} is only a preference: it is kept while it still
 * weighs the unit written for that food, the way a picked measure is on a publish
 * (FD-039), and dropped otherwise.
 */
public record KeptMatchDto(
        @NotBlank String name,
        Long compositionFoodId,
        Long foodItemId,
        Long foodMeasureId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Null(message = RetiredFields.BEDCA_FOOD_ID) Long bedcaFoodId) {

    /** A kept match that says nothing about BEDCA, which is every valid one. */
    public KeptMatchDto(String name, Long compositionFoodId, Long foodItemId, Long foodMeasureId) {
        this(name, compositionFoodId, foodItemId, foodMeasureId, null);
    }
}
