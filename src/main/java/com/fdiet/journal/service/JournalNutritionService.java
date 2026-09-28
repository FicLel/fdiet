package com.fdiet.journal.service;

import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.helpers.IPortionScaler;
import com.fdiet.diet.helpers.IPortionScaler.Weighed;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.service.INutritionService;
import com.fdiet.journal.model.ExtraFood;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * The same arithmetic the week is totalled by, over what was eaten beside it.
 *
 * <p>It borrows {@link IPortionScaler} rather than keeping a unit table of its
 * own. There is one answer to what a millilitre or a spoonful weighs, and two
 * tables of it would drift until a day's plan and that day's extras disagreed
 * about the same word. The household measure goes through the same door:
 * "1 cucharada de aceite" logged as an extra weighs what it weighs in the plan.
 */
@Service
public class JournalNutritionService implements IJournalNutritionService {

    private final INutritionService nutritionService;
    private final IPortionScaler portionScaler;

    public JournalNutritionService(INutritionService nutritionService,
                                   IPortionScaler portionScaler) {
        this.nutritionService = nutritionService;
        this.portionScaler = portionScaler;
    }

    @Override
    public NutritionDto of(ExtraFood extra) {
        if (extra == null || !extra.isMatched()) {
            return null;
        }
        Weighed weighed = weigh(extra);
        if (weighed == null) {
            return null;
        }
        NutritionDto per100g = extra.getBedcaFood() != null
                ? nutritionService.per100g(extra.getBedcaFood())
                : nutritionService.per100g(extra.getFoodItem());
        return per100g.isEmpty() ? null : per100g.scaled(weighed.factor());
    }

    /** Every entry lands in exactly one of the three counts. */
    @Override
    public NutritionSummaryDto summarise(Collection<ExtraFood> extras) {
        NutritionDto totals = NutritionDto.EMPTY;
        int counted = 0;
        int unmatched = 0;
        int unmeasured = 0;
        int byMeasure = 0;

        for (ExtraFood extra : extras) {
            if (!extra.isMatched()) {
                unmatched++;
                continue;
            }
            NutritionDto scaled = of(extra);
            if (scaled == null) {
                unmeasured++;
                continue;
            }
            totals = totals.plus(scaled);
            counted++;
            if (weigh(extra).byMeasure()) {
                byMeasure++;
            }
        }
        return new NutritionSummaryDto(
                totals, extras.size(), counted, unmatched, unmeasured, byMeasure);
    }

    private Weighed weigh(ExtraFood extra) {
        if (extra == null || !extra.isMatched()) {
            return null;
        }
        return portionScaler.weigh(extra.getQuantity(), extra.getUnit(), extra.getFoodMeasure(),
                extra.getBedcaFood() == null ? null : extra.getBedcaFood().getEdiblePortion());
    }
}
