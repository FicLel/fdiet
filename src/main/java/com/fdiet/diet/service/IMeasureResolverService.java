package com.fdiet.diet.service;

import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.model.ReferenceFoodMeasure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Chooses the household measure that weighs a written ingredient — the
 * counterpart of {@link IFoodResolverService}, which chooses its food. It owns
 * no table: the rows are the reference module's, and the rule that picks one
 * ({@code ReferenceMatcher}) lives there too. This is the diet's side of asking:
 * which ingredients need a measure at all, in a batch, and as entities a recipe
 * can point at.
 */
public interface IMeasureResolverService {

    /**
     * The measure each written ingredient is weighed by, in two batched calls
     * whatever the number of ingredients. An ingredient with no
     * composition-database food, written in a weight or volume, or in a word that
     * is no household measure, is absent — so is one nothing could be chosen for.
     *
     * @param foodOf the composition-database food each ingredient was matched to,
     *               or null
     */
    Map<DishIngredient, ReferenceFoodMeasure> measuresOf(List<DishIngredient> ingredients,
                                                         Function<DishIngredient, BedcaFood> foodOf,
                                                         Long dietId, String profile);

    /** The measure one food written in one unit may be weighed by, keeping {@code preferred} when it fits. */
    MeasureChoiceDto choose(BedcaFood food, String unit, PortionSize size, Long preferred, Long dietId,
                            String profile);

    /** The managed row behind a chosen measure, or null. */
    ReferenceFoodMeasure entityOf(FoodMeasureDto measure);

    /**
     * Refuses one diet's own measure for a library recipe: it goes with its diet,
     * and the shared recipe would go quietly unweighed everywhere. A published
     * row or the nutritionist's global criterion — no diet's — is allowed.
     */
    void requireNoDietMeasures(Collection<Long> measureIds);
}
