package com.fdiet.journal.service;

import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.helpers.IPortionScaler;
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
 * about the same word.
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
        BigDecimal factor = portionScaler.factorOf(extra.getQuantity(), extra.getUnit());
        if (factor == null) {
            return null;
        }
        NutritionDto per100g = extra.getBedcaFood() != null
                ? nutritionService.per100g(extra.getBedcaFood())
                : nutritionService.per100g(extra.getFoodItem());
        return per100g.isEmpty() ? null : per100g.scaled(factor);
    }

    /** Every entry lands in exactly one of the three counts. */
    @Override
    public NutritionSummaryDto summarise(Collection<ExtraFood> extras) {
        NutritionDto totals = NutritionDto.EMPTY;
        int counted = 0;
        int unmatched = 0;
        int unmeasured = 0;

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
        }
        return new NutritionSummaryDto(totals, extras.size(), counted, unmatched, unmeasured);
    }
}
