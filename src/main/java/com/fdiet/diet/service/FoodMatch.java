package com.fdiet.diet.service;

import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;

/**
 * What an ingredient's name was matched to: a CIQUAL or BLS composition food, or
 * a branded catalogue product. Never both.
 *
 * <p>Entities rather than DTOs, because the caller has to point an ingredient
 * at one — a service-to-service shape, not a layer crossing.
 */
public record FoodMatch(FoodItem foodItem, CompositionFood compositionFood) {

    public static FoodMatch of(CompositionFood food) {
        return new FoodMatch(null, food);
    }

    public static FoodMatch of(FoodItem item) {
        return new FoodMatch(item, null);
    }
}
