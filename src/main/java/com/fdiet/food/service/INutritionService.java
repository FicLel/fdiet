package com.fdiet.food.service;

import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.model.CompositionFigures;
import com.fdiet.food.model.FoodItem;

/**
 * Turns a stored food's published figures into the one set of units everything
 * downstream adds up in.
 *
 * <p>It lives in the food module because knowing what a kJ or a milligram of a
 * catalogue row means is food knowledge. Scaling those figures to the portion a
 * diet prescribes is the diet's business and happens there.
 *
 * <p>Nothing here writes: the conversion is derived on every read, so the
 * stored values stay exactly as published.
 */
public interface INutritionService {

    /**
     * A composition food's figures per 100 g of edible portion — BEDCA, CIQUAL
     * or BLS, which all publish each figure with its own unit.
     */
    NutritionDto per100g(CompositionFigures food);

    /** The branded product's composition per 100 g, as its label declares it. */
    NutritionDto per100g(FoodItem item);
}
