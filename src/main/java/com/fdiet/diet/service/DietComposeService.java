package com.fdiet.diet.service;

import com.fdiet.diet.dto.ComposeRequestDto;
import com.fdiet.diet.dto.ComposedFragmentDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * "Añadir por raciones": the composer writes text; it does not store an
 * ingredient of its own. Owns no table — the diet's profile is asked of
 * {@link IDietService}, the food of the food module, the measure of the
 * reference module.
 */
@Service
public class DietComposeService implements IDietComposeService {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal QUARTER = new BigDecimal("0.25");

    private final IDietService dietService;
    private final IRecipeService recipeService;
    private final ICompositionFoodService compositionFoodService;
    private final IReferenceService referenceService;
    private final IMeasureResolverService measureResolver;

    public DietComposeService(IDietService dietService,
                              IRecipeService recipeService,
                              ICompositionFoodService compositionFoodService,
                              IReferenceService referenceService,
                              IMeasureResolverService measureResolver) {
        this.dietService = dietService;
        this.recipeService = recipeService;
        this.compositionFoodService = compositionFoodService;
        this.referenceService = referenceService;
        this.measureResolver = measureResolver;
    }

    /**
     * The fragment is built from the food's own name — its Spanish one, or the
     * source's for a food the crosswalk names in no Spanish — and from a spelling
     * of the measure the parser reads; then it is read back through that same
     * parser, pinned to the food asked for, and what comes back is what the recipe
     * will hold. Pinned rather than matched by name, so a food without a Spanish
     * name still comes back matched.
     */
    @Override
    @Transactional(readOnly = true)
    public ComposedFragmentDto compose(ComposeRequestDto request) {
        if ((request.grams() == null) == (request.foodMeasureId() == null)) {
            throw new InvalidDietException(
                    "A composed food is a weight or a household measure: send grams or foodMeasureId");
        }
        CompositionFood food = compositionFoodService.entityById(request.compositionFoodId());
        String name = food.label().replaceAll("[()+:]", " ").replaceAll("\\s+", " ").trim();
        String profile = request.dietId() == null
                ? null
                : dietService.referenceProfileCode(request.dietId());

        String fragment;
        if (request.grams() != null) {
            fragment = name + " (" + amount(request.grams()) + " g" + stateWords(request.state()) + ")";
        } else {
            BigDecimal count = request.count() == null ? BigDecimal.ONE : request.count();
            FoodMeasureDto measure = referenceService.measureEntities(List.of(request.foodMeasureId()))
                    .values().stream().findFirst()
                    .map(referenceService::describe)
                    .orElseThrow(() -> new InvalidDietException(
                            "No household measure with id " + request.foodMeasureId()));
            boolean several = count.compareTo(BigDecimal.ONE) > 0;
            fragment = name + " (" + amount(count) + " "
                    + measure.measure().written(several, measure.size())
                    + stateWords(request.state()) + ")";
            MeasureChoiceDto choice = measureResolver.choose(food, measure.measure().label(),
                    measure.size(), measure.id(), request.dietId(), profile);
            if (choice.chosen() == null || !choice.chosen().id().equals(measure.id())) {
                throw new InvalidDietException("Household measure " + measure.id()
                        + " does not weigh " + food.label());
            }
        }

        RecipeDto read = recipeService.read(fragment, name, request.dietId(), profile,
                food.getId(), request.foodMeasureId(), List.of());
        return new ComposedFragmentDto(fragment, read.ingredients().get(0));
    }

    /** {@code 70}, {@code 0,5}, {@code 1/2}: a number the parser reads back as written. */
    private static String amount(BigDecimal value) {
        BigDecimal stripped = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (stripped.compareTo(HALF) == 0) {
            return "1/2";
        }
        if (stripped.compareTo(QUARTER) == 0) {
            return "1/4";
        }
        return stripped.toPlainString().replace('.', ',');
    }

    private static String stateWords(FoodState state) {
        if (state == null) {
            return "";
        }
        return switch (state) {
            case RAW -> " en crudo";
            case DRY -> " en seco";
            case COOKED -> " cocinado";
            case CANNED -> " en conserva";
            case DRAINED -> " escurrido";
            case UNSPECIFIED -> "";
        };
    }
}
