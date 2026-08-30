package com.fdiet.alternative.helpers;

import com.fdiet.food.dto.NutritionDto;

/**
 * How close two compositions are, on the components both of them publish.
 *
 * <p>It orders a list of candidates that a category has already vouched for. It
 * is never asked whether two foods are interchangeable — only which of two
 * meats is the nearer swap.
 */
public interface INutritionSimilarity {

    /**
     * 0–100, a hundred being the same figures. {@code null} when the two foods
     * share too few published components to be compared at all, so the caller
     * can leave them out rather than rank them on one number.
     */
    Integer score(NutritionDto reference, NutritionDto candidate);
}
