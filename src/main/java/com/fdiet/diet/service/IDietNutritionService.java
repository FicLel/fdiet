package com.fdiet.diet.service;

import com.fdiet.diet.domain.Serving;
import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.food.dto.NutritionDto;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * What a stored ingredient, and any group of them, works out to.
 *
 * <p>The composition figures come from the food module, which knows what a
 * kilojoule is; the portion arithmetic is here, because how much of a food a
 * diet prescribes is the diet's business.
 */
public interface IDietNutritionService {

    /**
     * This ingredient's own figures, for the quantity written. Null when it has
     * not been matched to a food, or when its unit cannot be weighed.
     */
    NutritionDto of(RecipeIngredient ingredient);

    /**
     * The grams of edible food the ingredient comes to — the weight its figures
     * are scaled from — or null when it cannot be weighed. What a ration count
     * divides.
     */
    BigDecimal edibleGrams(RecipeIngredient ingredient);

    /**
     * The total of these ingredients at the servings each is served in, with the
     * count of what did and did not contribute to it. Servings scale the figures
     * and never the counts: an ingredient is one ingredient however much of it
     * is on the plate.
     */
    NutritionSummaryDto summarise(Collection<Serving> servings);
}
