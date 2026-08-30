package com.fdiet.alternative.dto;

import com.fdiet.food.dto.NutritionDto;

import java.math.BigDecimal;

/**
 * One food offered in place of another.
 *
 * <p>It is already known to share the asked-for food's category — nothing here
 * crosses a shelf — so {@code score} answers the narrower question of which of
 * two meats is the nearer swap. It is 0–100, a hundred meaning the two
 * compositions read alike on the components both publish.
 *
 * <p>{@code nutrition} is per 100 g, always. {@code equivalentGrams} is how much
 * of this food carries the energy of the portion that was asked about, and
 * {@code equivalentPortion} its figures at that weight — both null when no
 * portion was given, and both null when either food's energy is unpublished,
 * because there is then nothing to hold equal.
 */
public record AlternativeDto(
        Long bedcaFoodId,
        String name,
        int score,
        NutritionDto nutrition,
        BigDecimal equivalentGrams,
        NutritionDto equivalentPortion) {
}
