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
 *       ("1 unidad", "1 cdta"), so its figures are unknown.
 * </ul>
 *
 * <p>{@code counted + unmatched + unmeasured == ingredients}, always. A total
 * is complete only when {@code counted == ingredients}.
 */
public record NutritionSummaryDto(
        NutritionDto totals,
        int ingredients,
        int counted,
        int unmatched,
        int unmeasured) {

    /** True when every ingredient contributed, so the totals stand on their own. */
    public boolean complete() {
        return ingredients > 0 && counted == ingredients;
    }
}
