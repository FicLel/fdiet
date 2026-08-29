package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.ResolveIngredientDto;

/**
 * Owns the stored diet: the {@code diets} row and the meals, dishes and
 * ingredients that hang off it. They are one aggregate — a dish exists only
 * inside its meal — so one service owns all four tables, and food is still
 * reached through the food module's own services.
 */
public interface IDietService {

    /**
     * Stores the submitted week as the diet in force, archiving the one it
     * replaces. Ingredients no food matches exactly are stored unmatched.
     */
    DietDto create(DietRequestDto request);

    /** Replaces the whole week of a stored diet. What is not sent is deleted. */
    DietDto update(Long id, DietRequestDto request);

    /** The diet in force now. */
    DietDto findActive();

    DietDto findById(Long id);

    /** The archived diets, most recently started first. */
    PageDto<DietSummaryDto> history(int page, int size);

    /**
     * A page of the diet's ingredients.
     *
     * @param resolved null for all of them, false for the ones still waiting to
     *                 be matched to a food, true for the rest
     * @param suggest  whether to attach the ranked candidates for each one,
     *                 which is what the fix-up screen is driven from
     */
    PageDto<DishIngredient> ingredients(
            Long dietId, Boolean resolved, boolean suggest, int page, int size);

    /** Matches one stored ingredient to a food, or corrects it. */
    DishIngredient resolveIngredient(Long dietId, Long ingredientId, ResolveIngredientDto change);
}
