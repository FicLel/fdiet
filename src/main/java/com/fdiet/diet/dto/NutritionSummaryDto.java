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
 *       ("1 unidad" with no measure attached), a range nobody settled yet, or
 *       weighed in another state than its food is published in, so its figures
 *       are unknown.
 * </ul>
 *
 * <p>{@code counted + unmatched + unmeasured == ingredients}, always. A total
 * is complete only when {@code counted == ingredients}.
 *
 * <p>{@code countedByMeasure} is the part of {@code counted} that was weighed
 * through a household measure rather than written in grams — "1 cdta" read as
 * 5 ml, "1 kiwi" as 80 g — so a total says how much of it rests on a
 * conversion.
 *
 * <p>{@code unmeasuredByState} is the part of {@code unmeasured} left out because
 * the quantity was weighed in one side of cooking and the food is published in
 * the other ({@code 55 g en seco} matched to {@code Lenteja, cocida}). It is never
 * totalled at the wrong state, and a cooking yield is only offered on the
 * ingredient ({@code yieldHint}), never applied (FD-052).
 */
public record NutritionSummaryDto(
        NutritionDto totals,
        int ingredients,
        int counted,
        int unmatched,
        int unmeasured,
        int countedByMeasure,
        int unmeasuredByState) {

    /** A summary with nothing left out for its state : the journal's, which does not check state yet (FD-061). */
    public NutritionSummaryDto(NutritionDto totals, int ingredients, int counted, int unmatched,
                               int unmeasured, int countedByMeasure) {
        this(totals, ingredients, counted, unmatched, unmeasured, countedByMeasure, 0);
    }

    /** True when every ingredient contributed, so the totals stand on their own. */
    public boolean complete() {
        return ingredients > 0 && counted == ingredients;
    }
}
