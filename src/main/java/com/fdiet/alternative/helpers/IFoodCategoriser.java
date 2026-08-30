package com.fdiet.alternative.helpers;

import com.fdiet.alternative.domain.FoodCategory;

/**
 * Reads a food's name and says which family it belongs to.
 *
 * <p>It decides, where {@link com.fdiet.food.helpers.INameMatcher} only ranks —
 * but it decides one thing only, and refuses when it cannot. A name no rule
 * claims comes back {@code null}, and a food with no category is offered no
 * alternatives at all rather than alternatives drawn from the wrong shelf.
 */
public interface IFoodCategoriser {

    /** The family that name belongs to, or {@code null} when no rule claims it. */
    FoodCategory of(String name);
}
