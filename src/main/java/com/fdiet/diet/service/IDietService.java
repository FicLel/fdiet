package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.dto.ParseDishRequestDto;
import com.fdiet.diet.dto.ResolveIngredientDto;

import java.time.DayOfWeek;

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

    /** Whether the diet is stored at all, without loading its week. */
    boolean exists(Long dietId);

    /**
     * Whether a dish sits at that place in the week — the day, the meal slot,
     * and its position within the meal.
     *
     * <p>Asked by {@code com.fdiet.journal}, which records what the patient
     * thought of a plate against the slot rather than against the dish row,
     * since a republish renumbers every row in the week. Only this service can
     * answer it: the dishes are its table.
     */
    boolean hasDishAt(Long dietId, DayOfWeek day, MealType mealType, int dishIndex);

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

    /**
     * Reads one written cell as a dish and its ingredients, matched against the
     * catalogues and priced, without storing a thing.
     *
     * <p>The parsing a diet is written by lives in one place. An editor that
     * re-implemented it would drift from the importer, and the two would then
     * disagree about what the same line of text means.
     */
    Dish parse(ParseDishRequestDto request);
}
