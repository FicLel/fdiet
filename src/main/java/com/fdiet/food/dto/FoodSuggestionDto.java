package com.fdiet.food.dto;

/**
 * A candidate for an ingredient nobody could match exactly — an offer, not a
 * decision.
 *
 * <p>Matching {@code 2 lonchas de jamón serrano} by similarity alone lands on
 * {@code Jamón asado}, and {@code 1 pan integral} on {@code Pan rallado}: close
 * enough to score well, wrong enough to put a false figure in someone's diet.
 * So these are ranked and shown, and the nutritionist picks one with a PATCH.
 *
 * <p>{@code score} is 0–100, how much of the food's name the ingredient text
 * accounts for. It orders the list; it does not authorise anything.
 */
public record FoodSuggestionDto(
        Long bedcaFoodId,
        String name,
        String foodGroup,
        int score) {
}
