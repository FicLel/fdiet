package com.fdiet.alternative.dto;

import com.fdiet.alternative.domain.EquivalenceBasis;
import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.food.dto.NutritionDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What can stand in for one food, and what was looked at to say so.
 *
 * <p><strong>The list travels with its counts.</strong> An empty
 * {@code alternatives} has three quite different meanings and the caller has to
 * be able to tell them apart: {@code category} is null when no rule recognised
 * the name, so nothing was ever eligible; {@code inCategory} is zero when the
 * category is known but the catalogue holds nothing else on that shelf; and
 * {@code ranked} is short of {@code inCategory} when foods were eligible but
 * published too few figures to be compared. A short list from a large
 * {@code inCategory} is the honest answer; a short list because nothing could be
 * read is a different one.
 *
 * <p>{@code nutrition} is the asked-for food per 100 g. {@code portion} is the
 * same figures at {@code grams}, and both it and {@code grams} are null unless a
 * portion was asked about. {@code basis} is what every equivalent weight holds
 * constant; {@code portionRations} the asked-for portion in the rations of
 * {@code profileCode}, null without one.
 */
public record FoodAlternativesDto(
        Long foodId,
        String name,
        FoodCategory category,
        String categoryLabel,
        NutritionDto nutrition,
        BigDecimal grams,
        NutritionDto portion,
        EquivalenceBasis basis,
        String profileCode,
        RationEquivalentDto portionRations,
        int inCategory,
        int ranked,
        List<AlternativeDto> alternatives) {
}
