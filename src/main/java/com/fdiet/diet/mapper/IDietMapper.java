package com.fdiet.diet.mapper;

import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.diet.service.FoodMatch;
import com.fdiet.reference.model.ReferenceFoodMeasure;

import java.time.DayOfWeek;
import java.util.List;

/** Moves a diet between its stored shape and the shape that crosses a boundary. */
public interface IDietMapper {

    /** The whole diet, its week ordered by day and by meal slot, with the totals. */
    DietDto toDto(DietPlan plan);

    /** The diet without its week, for the history listing. */
    DietSummaryDto toSummary(DietPlan plan);

    /** The stored meals as days, ordered, each carrying its own total. */
    List<DietDay> toDays(DietPlan plan);

    MealDto toDto(PlannedMeal meal);

    Dish toDto(PlannedDish dish);

    DishIngredient toDto(RecipeIngredient ingredient);

    /** A meal with no dishes yet; the caller adds them through {@code addDish}. */
    PlannedMeal toEntity(DayOfWeek day, MealDto meal);

    /** A dish with no ingredients yet. */
    RecipeDto toDto(Recipe recipe);

    /**
     * {@code match} may be null: an unmatched ingredient is still stored. So may
     * {@code measure}: an ingredient written in grams needs none, and one written
     * in a measure nothing weighs yet is stored unmeasured.
     */
    RecipeIngredient toEntity(DishIngredient ingredient, FoodMatch match,
                               ReferenceFoodMeasure measure);
}
