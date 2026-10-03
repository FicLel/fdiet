package com.fdiet.reference.domain;

/**
 * The rows that may be weighed by a household measure — each in a table another
 * context owns, which is why they are counted through
 * {@code IMeasureUsageCounter} rather than read from here.
 */
public enum MeasureUser {
    /** {@code recipe_ingredients.food_measure_id}, owned by the diet's recipe service. */
    RECIPE_INGREDIENT,
    /** {@code extra_foods.food_measure_id}, owned by the journal. */
    EXTRA_FOOD
}
