package com.fdiet.diet.domain;

import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.model.RecipeIngredient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One ingredient of a recipe as a plate serves it: the recipe's quantity for one
 * serving, times how many servings the plate is.
 *
 * <p>A shared recipe is written once, so the servings belong to the dish and the
 * multiplication happens on read. Nothing scaled is ever written back into the
 * recipe, which would change it for every other plate that serves it.
 */
public record Serving(RecipeIngredient ingredient, BigDecimal servings) {

    public Serving {
        servings = servings == null ? BigDecimal.ONE : servings;
    }

    /** Every ingredient of the plate, at the plate's servings. */
    public static List<Serving> of(PlannedDish dish) {
        return dish.getIngredients().stream()
                .map(ingredient -> new Serving(ingredient, dish.getServings()))
                .toList();
    }

    /** Every ingredient of every plate of these meals. */
    public static List<Serving> ofMeals(Collection<PlannedMeal> meals) {
        List<Serving> all = new ArrayList<>();
        for (PlannedMeal meal : meals) {
            for (PlannedDish dish : meal.getDishes()) {
                all.addAll(of(dish));
            }
        }
        return all;
    }

    /** A recipe read on its own: one serving of each ingredient. */
    public static List<Serving> single(Collection<RecipeIngredient> ingredients) {
        return ingredients.stream().map(ingredient -> new Serving(ingredient, BigDecimal.ONE)).toList();
    }
}
