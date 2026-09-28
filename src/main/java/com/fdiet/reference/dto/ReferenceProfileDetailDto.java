package com.fdiet.reference.dto;

import java.util.List;

/**
 * Everything one profile carries — its rations, how often they are
 * recommended, the meal energy shares it is read against — and every source
 * those figures come from, so the attribution can be drawn in the same pass.
 */
public record ReferenceProfileDetailDto(
        ReferenceProfileDto profile,
        List<RationDto> rations,
        List<RecommendationDto> recommendations,
        MealSharesDto mealShares,
        List<ReferenceSourceDto> sources) {
}
