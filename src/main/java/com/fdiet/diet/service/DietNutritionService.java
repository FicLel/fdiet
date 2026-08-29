package com.fdiet.diet.service;

import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.helpers.IPortionScaler;
import com.fdiet.diet.model.PlannedIngredient;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.service.INutritionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Scales a matched food to the portion the diet prescribes, and adds the
 * portions up.
 *
 * <p>It owns no repository and reads only what its caller already loaded, so a
 * whole week is totalled without a single extra query.
 */
@Service
public class DietNutritionService implements IDietNutritionService {

    private final INutritionService nutritionService;
    private final IPortionScaler portionScaler;

    public DietNutritionService(INutritionService nutritionService, IPortionScaler portionScaler) {
        this.nutritionService = nutritionService;
        this.portionScaler = portionScaler;
    }

    @Override
    public NutritionDto of(PlannedIngredient ingredient) {
        if (ingredient == null || !ingredient.isMatched()) {
            return null;
        }
        BigDecimal factor = portionScaler.factorOf(ingredient.getQuantity(), ingredient.getUnit());
        if (factor == null) {
            return null;
        }
        NutritionDto per100g = ingredient.getBedcaFood() != null
                ? nutritionService.per100g(ingredient.getBedcaFood())
                : nutritionService.per100g(ingredient.getFoodItem());
        return per100g.isEmpty() ? null : per100g.scaled(factor);
    }

    /**
     * Every ingredient lands in exactly one count, so a partial total can never
     * be mistaken for a complete one.
     */
    @Override
    public NutritionSummaryDto summarise(Collection<PlannedIngredient> ingredients) {
        NutritionDto totals = NutritionDto.EMPTY;
        int counted = 0;
        int unmatched = 0;
        int unmeasured = 0;

        for (PlannedIngredient ingredient : ingredients) {
            if (!ingredient.isMatched()) {
                unmatched++;
                continue;
            }
            NutritionDto scaled = of(ingredient);
            if (scaled == null) {
                unmeasured++;
                continue;
            }
            totals = totals.plus(scaled);
            counted++;
        }
        return new NutritionSummaryDto(
                totals, ingredients.size(), counted, unmatched, unmeasured);
    }
}
