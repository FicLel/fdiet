package com.fdiet.alternative.service;

import com.fdiet.alternative.dto.AlternativeQueryDto;
import com.fdiet.alternative.dto.FoodAlternativesDto;

import java.math.BigDecimal;

/**
 * What else a diet could put on the plate in place of a given food.
 *
 * <p>It owns no table. The composition foods (CIQUAL 2025 / BLS 4.0) are reached through
 * {@link com.fdiet.food.service.ICompositionFoodService} and read through
 * {@link com.fdiet.food.service.INutritionService}, the same way the diet module
 * reaches it — this context adds a judgement about foods, not a store of them.
 *
 * <p>The judgement is in two halves and they are not interchangeable. A
 * category decides <em>who is eligible</em>: grilled chicken may be replaced by
 * a fish or another meat and never by a lettuce, however well the figures happen
 * to line up. Composition then decides <em>the order</em> of the eligible. The
 * arithmetic is never allowed to make the first decision, because it would; a
 * large enough portion of anything meets a small enough portion of anything
 * else on paper.
 */
public interface IAlternativeService {

    /**
     * Alternatives to the CIQUAL or BLS food with that id.
     *
     * @param foodId   the food being replaced; 404 if the catalogue has no such row
     * @param limit    how many to hand back, best first
     * @param grams    the portion the diet prescribes, or null to answer per 100 g
     * @param sameFood whether other preparations of the same food stay in the
     *                 list. False by default, because six more cuts of chicken
     *                 are not an alternative to chicken; true is what to pass
     *                 when the question really is "which other cheese", since
     *                 there the same word is the whole family
     */
    default FoodAlternativesDto forFoodId(Long foodId, int limit, BigDecimal grams, boolean sameFood) {
        return forFoodId(foodId, AlternativeQueryDto.byEnergy(limit, grams, sameFood));
    }

    /**
     * The same, with the equivalence basis and the reference profile named: how
     * much of each alternative carries the carbohydrate (or protein, fat,
     * energy) of the portion, and how many of the profile's rations that is.
     * The basis never widens who is eligible — the category still decides that.
     */
    FoodAlternativesDto forFoodId(Long foodId, AlternativeQueryDto query);

    /**
     * The same answer for a food named rather than pointed at, which is how a
     * diet is written.
     *
     * <p>The name has to be a Spanish name or alias of fdiet's crosswalk, matched exactly — the
     * comparison is case- and accent-insensitive, so {@code lechuga} finds
     * {@code Lechuga}, and anything less than exact is a 404 rather than a
     * guess. An ingredient whose name is not in the catalogue is matched first
     * through {@code GET /api/diets/{id}/ingredients?suggest=true}, by a person.
     */
    default FoodAlternativesDto forName(String name, int limit, BigDecimal grams, boolean sameFood) {
        return forName(name, AlternativeQueryDto.byEnergy(limit, grams, sameFood));
    }

    FoodAlternativesDto forName(String name, AlternativeQueryDto query);
}
