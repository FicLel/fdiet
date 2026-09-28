package com.fdiet.diet.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * A correction to one stored ingredient. Every field is optional: sending only
 * {@code bedcaFoodId} matches the ingredient to a composition-database food and
 * leaves the rest alone, which is the ordinary fix after an import.
 *
 * <p>Matching to one half of the catalogue clears the other, so an ingredient
 * never points at two foods at once.
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
        Long bedcaFoodId,
        String name,
        @Positive BigDecimal quantity,
        String unit,
        Long foodMeasureId) {
}
