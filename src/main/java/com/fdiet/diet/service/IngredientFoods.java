package com.fdiet.diet.service;

import com.fdiet.common.helper.Texts;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;

import java.util.Map;

/**
 * The ways a batch of written ingredients finds its foods: the id the caller
 * gave, on either half of the catalogue, or the name matched against both. None
 * may find one, and then the ingredient is stored unmatched.
 *
 * <p>Built by {@link IIngredientFoodService#foodsOf} in a handful of batched
 * calls; probing it is O(1) per ingredient.
 */
public record IngredientFoods(Map<Long, FoodItem> items,
                              Map<Long, CompositionFood> generic,
                              Map<String, FoodMatch> byName) {

    /** What the ingredient was matched to, or null. */
    public FoodMatch of(DishIngredient ingredient) {
        if (ingredient.compositionFoodId() != null) {
            return FoodMatch.of(generic.get(ingredient.compositionFoodId()));
        }
        if (ingredient.foodItemId() != null) {
            return FoodMatch.of(items.get(ingredient.foodItemId()));
        }
        String key = Texts.normaliseName(ingredient.name());
        return key == null ? null : byName.get(key);
    }

    /** The composition food the ingredient found, or null. */
    public CompositionFood compositionFood(DishIngredient ingredient) {
        FoodMatch match = of(ingredient);
        return match == null ? null : match.compositionFood();
    }
}
