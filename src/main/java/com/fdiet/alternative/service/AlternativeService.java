package com.fdiet.alternative.service;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.alternative.dto.AlternativeDto;
import com.fdiet.alternative.dto.FoodAlternativesDto;
import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.alternative.helpers.INutritionSimilarity;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.exception.BedcaFoodNotFoundException;
import com.fdiet.food.helpers.INameMatcher;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.INutritionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Puts the shelf and the arithmetic together, in that order.
 *
 * <p>One pass over the catalogue answers a request: the category is read off
 * every name, foods on a different shelf are dropped before any figure is
 * looked at, and what survives is ordered by composition. The composition
 * database is 957 rows, so that pass is one query and a few thousand
 * comparisons — a hundredth of the work of asking the database a question per
 * candidate, and it needs no index of its own to fall stale after a sync.
 */
@Service
public class AlternativeService implements IAlternativeService {

    /** Composition figures are published per this many grams. */
    private static final BigDecimal PER = new BigDecimal("100");

    private static final int FACTOR_SCALE = 8;

    /** A diet is written in whole grams, so an equivalent portion is too. */
    private static final int GRAMS_SCALE = 0;

    private final IBedcaFoodService bedcaFoodService;
    private final INutritionService nutritionService;
    private final IFoodCategoriser foodCategoriser;
    private final INutritionSimilarity nutritionSimilarity;
    private final INameMatcher nameMatcher;

    public AlternativeService(IBedcaFoodService bedcaFoodService,
                              INutritionService nutritionService,
                              IFoodCategoriser foodCategoriser,
                              INutritionSimilarity nutritionSimilarity,
                              INameMatcher nameMatcher) {
        this.bedcaFoodService = bedcaFoodService;
        this.nutritionService = nutritionService;
        this.foodCategoriser = foodCategoriser;
        this.nutritionSimilarity = nutritionSimilarity;
        this.nameMatcher = nameMatcher;
    }

    @Override
    @Transactional(readOnly = true)
    public FoodAlternativesDto forFoodId(Long foodId, int limit, BigDecimal grams, boolean sameFood) {
        return alternativesTo(bedcaFoodService.entityById(foodId), limit, grams, sameFood);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodAlternativesDto forName(String name, int limit, BigDecimal grams, boolean sameFood) {
        BedcaFood food = bedcaFoodService.entitiesByName(List.of(name))
                .get(IBedcaFoodService.normalise(name));
        if (food == null) {
            throw new BedcaFoodNotFoundException(name);
        }
        return alternativesTo(food, limit, grams, sameFood);
    }

    /**
     * A food with no category is answered honestly and cheaply: the catalogue is
     * never read, because there is no shelf to read it for.
     */
    private FoodAlternativesDto alternativesTo(BedcaFood reference,
                                               int limit,
                                               BigDecimal grams,
                                               boolean sameFood) {
        NutritionDto nutrition = nutritionService.per100g(reference);
        BigDecimal portionFactor = factorOf(grams);
        NutritionDto portion = portionFactor == null ? null : nutrition.scaled(portionFactor);

        FoodCategory category = foodCategoriser.of(reference.getName());
        if (category == null) {
            return new FoodAlternativesDto(reference.getId(), reference.getName(),
                    null, null, nutrition, grams, portion, 0, 0, List.of());
        }

        String head = sameFood ? null : headOf(reference.getName());
        List<BedcaFood> eligible = eligible(reference, category, head);

        List<AlternativeDto> ranked = new ArrayList<>();
        for (BedcaFood candidate : eligible) {
            NutritionDto theirs = nutritionService.per100g(candidate);
            Integer score = nutritionSimilarity.score(nutrition, theirs);
            if (score == null) {
                continue;
            }
            ranked.add(offer(candidate, theirs, score, nutrition, grams));
        }
        ranked.sort(Comparator.comparingInt(AlternativeDto::score).reversed()
                .thenComparing(AlternativeDto::name));

        return new FoodAlternativesDto(
                reference.getId(), reference.getName(), category, category.label(),
                nutrition, grams, portion,
                eligible.size(), ranked.size(),
                ranked.stream().limit(limit).toList());
    }

    /**
     * The foods on the same shelf, without the food itself and — unless the
     * caller asked for them — without its other preparations. Sharing a leading
     * word is what makes {@code Pollo, muslo, asado} another cut of the same
     * bird rather than an answer to {@code Pollo, pechuga, plancha}.
     */
    private List<BedcaFood> eligible(BedcaFood reference, FoodCategory category, String head) {
        List<BedcaFood> eligible = new ArrayList<>();
        for (BedcaFood candidate : bedcaFoodService.entitiesAll()) {
            if (Objects.equals(candidate.getId(), reference.getId())
                    || category != foodCategoriser.of(candidate.getName())
                    || (head != null && head.equals(headOf(candidate.getName())))) {
                continue;
            }
            eligible.add(candidate);
        }
        return eligible;
    }

    private AlternativeDto offer(BedcaFood candidate,
                                 NutritionDto theirs,
                                 int score,
                                 NutritionDto reference,
                                 BigDecimal grams) {
        BigDecimal equivalentGrams = equivalentGrams(reference, theirs, grams);
        BigDecimal equivalentFactor = factorOf(equivalentGrams);
        return new AlternativeDto(
                candidate.getId(), candidate.getName(), score, theirs,
                equivalentGrams,
                equivalentFactor == null ? null : theirs.scaled(equivalentFactor));
    }

    /**
     * How much of the alternative carries the energy of the portion asked
     * about. Energy is what the equivalence holds constant because it is the
     * one figure every food in the catalogue publishes; a food whose energy is
     * unpublished, or which has none to speak of, gets no equivalent weight
     * rather than an invented one.
     */
    private BigDecimal equivalentGrams(NutritionDto reference, NutritionDto candidate, BigDecimal grams) {
        if (grams == null || reference.energyKcal() == null || candidate.energyKcal() == null
                || candidate.energyKcal().signum() <= 0) {
            return null;
        }
        return grams.multiply(reference.energyKcal())
                .divide(candidate.energyKcal(), GRAMS_SCALE, RoundingMode.HALF_UP);
    }

    /** What to multiply a per-100 g figure by to get it for {@code grams}. */
    private BigDecimal factorOf(BigDecimal grams) {
        return grams == null || grams.signum() <= 0
                ? null
                : grams.divide(PER, FACTOR_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The first word of a name that carries any meaning — {@code pollo} out of
     * {@code Pollo, pechuga, plancha} — read with the food module's own
     * tokeniser so that plurals and preparation words are dropped the same way
     * here as they are when an ingredient is matched.
     */
    private String headOf(String name) {
        Set<String> tokens = nameMatcher.tokens(name);
        return tokens.isEmpty() ? null : tokens.iterator().next();
    }
}
