package com.fdiet.diet.service;

import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.helpers.IPortionScaler;
import com.fdiet.diet.helpers.IPortionScaler.MeasureWeight;
import com.fdiet.diet.helpers.IPortionScaler.Weighed;
import com.fdiet.diet.model.PlannedIngredient;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.service.INutritionService;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final INutritionService nutritionService;
    private final IPortionScaler portionScaler;

    public DietNutritionService(INutritionService nutritionService, IPortionScaler portionScaler) {
        this.nutritionService = nutritionService;
        this.portionScaler = portionScaler;
    }

    @Override
    public NutritionDto of(PlannedIngredient ingredient) {
        Weighed weighed = weigh(ingredient);
        if (weighed == null) {
            return null;
        }
        NutritionDto per100g = ingredient.getBedcaFood() != null
                ? nutritionService.per100g(ingredient.getBedcaFood())
                : nutritionService.per100g(ingredient.getFoodItem());
        return per100g.isEmpty() ? null : per100g.scaled(weighed.factor());
    }

    @Override
    public BigDecimal edibleGrams(PlannedIngredient ingredient) {
        Weighed weighed = weigh(ingredient);
        return weighed == null ? null : weighed.factor().multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
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
        int byMeasure = 0;

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
            if (weigh(ingredient).byMeasure()) {
                byMeasure++;
            }
        }
        return new NutritionSummaryDto(
                totals, ingredients.size(), counted, unmatched, unmeasured, byMeasure);
    }

    /**
     * The quantity as a multiple of 100 g: straight from the unit when it is a
     * weight or a volume, through the attached household measure when the unit
     * is that measure, and not at all otherwise. A measure attached for
     * "cucharada" does not weigh a quantity since rewritten as "2 lonchas".
     */
    private Weighed weigh(PlannedIngredient ingredient) {
        if (ingredient == null || !ingredient.isMatched()) {
            return null;
        }
        ReferenceFoodMeasure measure = ingredient.getFoodMeasure();
        MeasureWeight weight = null;
        if (measure != null && HouseholdMeasure.ofUnit(ingredient.getUnit())
                .filter(written -> written == measure.getMeasure()).isPresent()) {
            weight = new MeasureWeight(
                    measure.gramsPerMeasure(),
                    measure.getWeightBasis() == WeightBasis.GROSS,
                    ingredient.getBedcaFood() == null ? null : ingredient.getBedcaFood().getEdiblePortion());
        }
        return portionScaler.weigh(ingredient.getQuantity(), ingredient.getUnit(), weight);
    }
}
