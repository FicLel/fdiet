package com.fdiet.diet.service;

import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.food.dto.CompositionSuggestionDto;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.reference.domain.FoodState;

import java.util.List;

/**
 * The food side of a recipe ingredient: which catalogue food a batch of written
 * ingredients points at, and which composition foods to offer one nobody could
 * match. Owns no table — the foods are the food module's, asked through its
 * services; this is the diet's way of asking, kept out of
 * {@link IRecipeService} so that one stays about recipes.
 */
public interface IIngredientFoodService {

    /**
     * Every food these ingredients need, in a handful of batched calls whatever
     * their number: the ones named by id, on either half of the catalogue, and
     * the ones matched by name ({@link IFoodResolverService}). An id nothing
     * carries is a 400, not a food to guess at.
     */
    IngredientFoods foodsOf(List<DishIngredient> ingredients);

    /**
     * The best composition foods for an unmatched ingredient, with any whose name
     * states the other side of raw/cooked from {@code written} moved to the end.
     * An order, never a match. No query.
     */
    List<CompositionSuggestionDto> suggestionsFor(String name, FoodState written);

    /** A composition food a person picked, as an entity to point at; 404 when unknown. */
    CompositionFood compositionFood(Long id);

    /** A branded product a person picked, as an entity to point at; 404 when unknown. */
    FoodItem foodItem(Long id);
}
