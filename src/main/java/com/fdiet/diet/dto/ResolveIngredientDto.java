package com.fdiet.diet.dto;

import com.fdiet.common.helper.RetiredFields;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * A correction to one stored ingredient. Every field is optional: sending only
 * {@code compositionFoodId} matches the ingredient to a CIQUAL or BLS food and
 * leaves the rest alone, which is the ordinary fix after an import.
 *
 * <p>Matching to one half of the catalogue clears the other, so an ingredient
 * never points at two foods at once. {@code bedcaFoodId} is read only to be
 * refused: BEDCA foods are no longer matched to (FD-033), and a value there is a
 * 400.
 *
 * <p>{@code foodMeasureId} picks the household measure that weighs the
 * quantity — one of the rows {@code GET /api/reference/measures} offers for this
 * food and unit, or one of the diet's own. A change of food or unit that the
 * attached measure no longer fits takes the measure away, and attaches another
 * only when the choice is not a judgement.
 *
 * <p>A null field means "leave it", so this cannot be used to unmatch an
 * ingredient — that is deliberate, since matching is the direction the fix-up
 * screen moves in.
 */
public record ResolveIngredientDto(
        Long foodItemId,
        Long compositionFoodId,
        String name,
        @Positive BigDecimal quantity,
        String unit,
        Long foodMeasureId,
        @Null(message = RetiredFields.BEDCA_FOOD_ID) Long bedcaFoodId) {

    /** A correction that says nothing about BEDCA, which is every valid one. */
    public ResolveIngredientDto(Long foodItemId, Long compositionFoodId, String name,
                                BigDecimal quantity, String unit, Long foodMeasureId) {
        this(foodItemId, compositionFoodId, name, quantity, unit, foodMeasureId, null);
    }
}
