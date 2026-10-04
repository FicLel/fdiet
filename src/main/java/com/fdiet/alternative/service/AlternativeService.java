package com.fdiet.alternative.service;

import com.fdiet.alternative.domain.EquivalenceBasis;
import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.alternative.dto.AlternativeDto;
import com.fdiet.alternative.dto.AlternativeQueryDto;
import com.fdiet.alternative.dto.FoodAlternativesDto;
import com.fdiet.alternative.dto.RationEquivalentDto;
import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.alternative.helpers.INutritionSimilarity;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.exception.CompositionFoodNotFoundException;
import com.fdiet.food.helpers.INameMatcher;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.INutritionService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.exception.ReferenceNotFoundException;
import com.fdiet.reference.service.IReferenceService;
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
 * every Spanish name, foods on a different shelf are dropped before any figure is
 * looked at, and what survives is ordered by composition. Only the composition
 * foods fdiet's crosswalk names in Spanish can be read for a family at all
 * (CIQUAL and BLS publish French, German and English names), so that pass is one
 * query over the crosswalked foods — 123 today, FD-036 grows it — and it needs
 * no index of its own to fall stale after a sync.
 */
@Service
public class AlternativeService implements IAlternativeService {

    /** Composition figures are published per this many grams. */
    private static final BigDecimal PER = new BigDecimal("100");

    private static final int FACTOR_SCALE = 8;

    /** A diet is written in whole grams, so an equivalent portion is too. */
    private static final int GRAMS_SCALE = 0;

    private final ICompositionFoodService compositionFoodService;
    private final INutritionService nutritionService;
    private final IFoodCategoriser foodCategoriser;
    private final INutritionSimilarity nutritionSimilarity;
    /** A ration count is read to one decimal: "1,2 raciones", never "1,1834". */
    private static final int RATIONS_SCALE = 1;

    private final INameMatcher nameMatcher;
    private final IReferenceService referenceService;

    public AlternativeService(ICompositionFoodService compositionFoodService,
                              INutritionService nutritionService,
                              IFoodCategoriser foodCategoriser,
                              INutritionSimilarity nutritionSimilarity,
                              INameMatcher nameMatcher,
                              IReferenceService referenceService) {
        this.compositionFoodService = compositionFoodService;
        this.nutritionService = nutritionService;
        this.foodCategoriser = foodCategoriser;
        this.nutritionSimilarity = nutritionSimilarity;
        this.nameMatcher = nameMatcher;
        this.referenceService = referenceService;
    }

    @Override
    @Transactional(readOnly = true)
    public FoodAlternativesDto forFoodId(Long foodId, AlternativeQueryDto query) {
        requireProfile(query.profileCode());
        return alternativesTo(compositionFoodService.entityById(foodId), query);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodAlternativesDto forName(String name, AlternativeQueryDto query) {
        requireProfile(query.profileCode());
        CompositionFood food = compositionFoodService.entitiesByName(List.of(name))
                .get(Texts.normaliseName(name));
        if (food == null) {
            throw new CompositionFoodNotFoundException(name);
        }
        return alternativesTo(food, query);
    }

    /** A profile nobody loaded is the reference module's 404, not a silent "no rations". */
    private void requireProfile(String code) {
        if (code != null && !referenceService.profileExists(code)) {
            throw ReferenceNotFoundException.profile(code);
        }
    }

    /**
     * A food with no category is answered honestly and cheaply: the catalogue is
     * never read, because there is no shelf to read it for. A food the crosswalk
     * names in no Spanish has none.
     */
    private FoodAlternativesDto alternativesTo(CompositionFood reference, AlternativeQueryDto query) {
        BigDecimal grams = query.grams();
        EquivalenceBasis basis = query.basis();
        String profile = query.profileCode();
        NutritionDto nutrition = nutritionService.per100g(reference);
        BigDecimal portionFactor = factorOf(grams);
        NutritionDto portion = portionFactor == null ? null : nutrition.scaled(portionFactor);
        RationEquivalentDto portionRations = rationsOf(profile, reference, grams);

        FoodCategory category = categoryOf(reference);
        if (category == null) {
            return new FoodAlternativesDto(reference.getId(), reference.label(), reference.getSource(),
                    null, null, nutrition, grams, portion, basis, profile, portionRations,
                    0, 0, List.of());
        }

        String head = query.sameFood() ? null : headOf(reference.getNameEs());
        List<CompositionFood> eligible = eligible(reference, category, head);

        List<AlternativeDto> ranked = new ArrayList<>();
        for (CompositionFood candidate : eligible) {
            NutritionDto theirs = nutritionService.per100g(candidate);
            Integer score = nutritionSimilarity.score(nutrition, theirs);
            if (score == null) {
                continue;
            }
            ranked.add(offer(candidate, theirs, score, nutrition, grams, basis, profile));
        }
        ranked.sort(Comparator.comparingInt(AlternativeDto::score).reversed()
                .thenComparing(AlternativeDto::name));

        return new FoodAlternativesDto(
                reference.getId(), reference.label(), reference.getSource(), category,
                category.label(),
                nutrition, grams, portion, basis, profile, portionRations,
                eligible.size(), ranked.size(),
                ranked.stream().limit(query.limit()).toList());
    }

    /**
     * The foods on the same shelf, without the food itself and — unless the
     * caller asked for them — without its other preparations. Sharing a leading
     * word is what makes {@code Pollo, muslo, asado} another cut of the same
     * bird rather than an answer to {@code Pollo, pechuga, plancha}.
     */
    private List<CompositionFood> eligible(CompositionFood reference, FoodCategory category,
                                           String head) {
        List<CompositionFood> eligible = new ArrayList<>();
        for (CompositionFood candidate : compositionFoodService.entitiesNamed()) {
            if (Objects.equals(candidate.getId(), reference.getId())
                    || category != categoryOf(candidate)
                    || (head != null && head.equals(headOf(candidate.getNameEs())))) {
                continue;
            }
            eligible.add(candidate);
        }
        return eligible;
    }

    private AlternativeDto offer(CompositionFood candidate,
                                 NutritionDto theirs,
                                 int score,
                                 NutritionDto reference,
                                 BigDecimal grams,
                                 EquivalenceBasis basis,
                                 String profile) {
        BigDecimal equivalentGrams = equivalentGrams(reference, theirs, grams, basis);
        BigDecimal equivalentFactor = factorOf(equivalentGrams);
        return new AlternativeDto(
                candidate.getId(), candidate.label(), candidate.getSource(), score, theirs,
                equivalentGrams,
                equivalentFactor == null ? null : theirs.scaled(equivalentFactor),
                rationsOf(profile, candidate, equivalentGrams));
    }

    /**
     * How much of the alternative carries the same figure as the portion asked
     * about: energy by default, because it is the figure nearly every food
     * publishes, or the grams of one macronutrient. Not every source does — 145
     * CIQUAL foods publish no energy — so a food that leaves the figure
     * unpublished, either side, or carries too little of it to be weighed against
     * (the protein of a lettuce), gets no equivalent weight rather than an
     * invented one; so does a portion that carries none of it.
     */
    private BigDecimal equivalentGrams(NutritionDto reference, NutritionDto candidate,
                                       BigDecimal grams, EquivalenceBasis basis) {
        BigDecimal held = basis.of(reference);
        if (grams == null || held == null || held.signum() <= 0 || !basis.carries(candidate)) {
            return null;
        }
        return grams.multiply(held)
                .divide(basis.of(candidate), GRAMS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * A weight of a food read in the profile's rations for it, or null when
     * there is no honest count: no profile, no group for the food, a ration with
     * no weight, or a ration defined in another state than the food (a cooked
     * food against a dry ration), which would need a yield somebody has to choose.
     */
    private RationEquivalentDto rationsOf(String profile, CompositionFood food, BigDecimal grams) {
        if (profile == null || grams == null || grams.signum() <= 0) {
            return null;
        }
        RationDto ration = referenceService.countingRation(profile, food.getId(), food.getNameEs());
        if (ration == null
                || FoodState.disagree(FoodState.ofFoodName(food.getNameEs()), ration.state())) {
            return null;
        }
        BigDecimal[] weight = ration.edibleWeight(food.getEdiblePortion());
        if (weight == null) {
            return null;
        }
        return new RationEquivalentDto(ration.groupCode(), ration.groupLabel(),
                grams.divide(weight[1], RATIONS_SCALE, RoundingMode.HALF_UP),
                grams.divide(weight[0], RATIONS_SCALE, RoundingMode.HALF_UP),
                ration.sourceShortName(), ration.pageRef());
    }

    /** The family a food's Spanish name reads as; null for a food the crosswalk does not name. */
    private FoodCategory categoryOf(CompositionFood food) {
        return food.getNameEs() == null ? null : foodCategoriser.of(food.getNameEs());
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
        if (name == null) {
            return null;
        }
        Set<String> tokens = nameMatcher.tokens(name);
        return tokens.isEmpty() ? null : tokens.iterator().next();
    }
}
