package com.fdiet.food.dto;

import com.fdiet.food.model.CompositionSource;

/**
 * A CIQUAL or BLS food offered for an ingredient nobody could match exactly — an
 * offer, not a decision.
 *
 * <p>Matching {@code 2 lonchas de jamón serrano} by similarity alone lands on the
 * wrong ham, and {@code 1 pan integral} on breadcrumbs: close enough to score
 * well, wrong enough to put a false figure in someone's diet. So these are ranked
 * and shown, and the nutritionist picks one with a PATCH.
 *
 * <p>{@code name} is fdiet's Spanish name for the food (only crosswalked foods are
 * offered, since the ingredient is Spanish text). {@code source} and
 * {@code sourceLabel} say which table it comes from, because the two measure
 * protein and energy differently. {@code score} is 0–100, how much of the food's
 * name the ingredient text accounts for; it orders the list and authorises
 * nothing.
 */
public record CompositionSuggestionDto(
        Long compositionFoodId,
        String name,
        CompositionSource source,
        String sourceLabel,
        int score) {
}
