package com.fdiet.journal.service;

import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.journal.model.ExtraFood;

import java.util.Collection;

/**
 * Scales a matched extra to the quantity that was logged, and adds a day of
 * them up.
 *
 * <p>The mirror of {@link com.fdiet.diet.service.IDietNutritionService}, over
 * this context's own aggregate. It owns no repository and reads only what its
 * caller already loaded.
 */
public interface IJournalNutritionService {

    /**
     * The entry's figures, or null when nothing was matched or the unit is one
     * nothing can weigh. Null rather than zero: an unweighable spoonful is not
     * an entry worth nothing.
     */
    NutritionDto of(ExtraFood extra);

    /** A day's off-plan total, with the counts it was worked out over. */
    NutritionSummaryDto summarise(Collection<ExtraFood> extras);
}
