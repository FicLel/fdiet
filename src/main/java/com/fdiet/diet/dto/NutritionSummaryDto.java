package com.fdiet.diet.dto;

import com.fdiet.food.dto.NutritionDto;

/**
 * What a meal, a day or a whole week adds up to — and how much of it is
 * actually accounted for.
 *
 * <p>The counts are not decoration. Most of an imported week starts unmatched,
 * and a total over half the ingredients looks exactly like a total over all of
 * them unless something says otherwise. So every ingredient lands in exactly
 * one of three counts:
 *
 * <ul>
 *   <li>{@code counted} — matched to a food and measured in a unit that scales,
 *       so it is in {@code totals};
 *   <li>{@code unmatched} — no food yet, waiting on the fix-up screen;
 *   <li>{@code unmeasured} — matched, but written in a unit nothing can weigh
 *       ("1 unidad" with no measure attached), so its figures are unknown.
 * </ul>
 *
 * <p>{@code counted + unmatched + unmeasured == ingredients}, always. A total
 * is complete only when {@code counted == ingredients}.
 *
 * <p>{@code countedByMeasure} is the part of {@code counted} that was weighed
 * through a household measure rather than written in grams — "1 cdta" read as
 * 5 ml, "1 kiwi" as 80 g — so a total says how much of it rests on a
 * conversion.
 */
public record NutritionSummaryDto(
        NutritionDto totals,
        int ingredients,
        int counted,
        int unmatched,
        int unmeasured,
        int countedByMeasure) {

    /** A summary with nothing weighed through a household measure. */
    public NutritionSummaryDto(NutritionDto totals, int ingredients, int counted, int unmatched,
                               int unmeasured) {
        this(totals, ingredients, counted, unmatched, unmeasured, 0);
    }

    /** True when every ingredient contributed, so the totals stand on their own. */
    public boolean complete() {
        return ingredients > 0 && counted == ingredients;
    }
}
