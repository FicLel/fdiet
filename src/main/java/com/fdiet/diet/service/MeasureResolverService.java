package com.fdiet.diet.service;

import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.helpers.IPortionScaler;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
public class MeasureResolverService implements IMeasureResolverService {

    private final IReferenceService referenceService;
    private final IPortionScaler portionScaler;

    public MeasureResolverService(IReferenceService referenceService, IPortionScaler portionScaler) {
        this.referenceService = referenceService;
        this.portionScaler = portionScaler;
    }

    /**
     * One call chooses for every ingredient (a query for the diet's criteria and
     * one for the global criteria of the foods named, the published rows already
     * in memory), and one batched load fetches the rows chosen. A measure the
     * request carries is kept when it still fits the food and the unit;
     * otherwise the rule decides again, and "decides" means only when the choice
     * is not a judgement. O(n) over the ingredients.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<DishIngredient, ReferenceFoodMeasure> measuresOf(
            List<DishIngredient> ingredients, Function<DishIngredient, BedcaFood> foodOf,
            Long dietId, String profile) {
        List<DishIngredient> written = new ArrayList<>();
        List<MeasureQueryDto> queries = new ArrayList<>();
        for (DishIngredient ingredient : ingredients) {
            BedcaFood food = foodOf.apply(ingredient);
            if (food == null || portionScaler.weighsDirectly(ingredient.unit())
                    || HouseholdMeasure.ofUnit(ingredient.unit()).isEmpty()) {
                continue;
            }
            written.add(ingredient);
            queries.add(MeasureQueryDto.byNameOnly(food.getName(), ingredient.unit(),
                    ingredient.size(), ingredient.foodMeasureId()));
        }
        if (queries.isEmpty()) {
            return Map.of();
        }
        List<MeasureChoiceDto> choices = referenceService.chooseMeasures(queries, dietId, profile);
        Map<Long, ReferenceFoodMeasure> entities = referenceService.measureEntities(choices.stream()
                .map(MeasureChoiceDto::chosen).filter(Objects::nonNull).map(FoodMeasureDto::id)
                .distinct().toList());

        // Two ingredients written exactly alike ask the same question, so they share the answer.
        Map<DishIngredient, ReferenceFoodMeasure> chosen = new HashMap<>();
        for (int at = 0; at < written.size(); at++) {
            FoodMeasureDto choice = choices.get(at).chosen();
            if (choice != null) {
                chosen.put(written.get(at), entities.get(choice.id()));
            }
        }
        return chosen;
    }

    @Override
    @Transactional(readOnly = true)
    public MeasureChoiceDto choose(BedcaFood food, String unit, PortionSize size, Long preferred,
                                   Long dietId, String profile) {
        if (food == null || portionScaler.weighsDirectly(unit)) {
            return MeasureChoiceDto.NONE;
        }
        return referenceService.chooseMeasures(List.of(MeasureQueryDto.byNameOnly(food.getName(),
                unit, size, preferred)), dietId, profile).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public ReferenceFoodMeasure entityOf(FoodMeasureDto measure) {
        if (measure == null) {
            return null;
        }
        return referenceService.measureEntities(List.of(measure.id())).get(measure.id());
    }

    @Override
    @Transactional(readOnly = true)
    public void requireNoDietMeasures(Collection<Long> measureIds) {
        if (measureIds.isEmpty()) {
            return;
        }
        List<Long> own = referenceService.measureEntities(measureIds).values().stream()
                .filter(ReferenceFoodMeasure::isDietOwn)
                .map(ReferenceFoodMeasure::getId)
                .toList();
        if (!own.isEmpty()) {
            throw new InvalidDietException("A library recipe is shared by every diet, so it cannot "
                    + "be weighed by one diet's own measure " + own + ". Pick a published measure or "
                    + "one of your global criteria, or write the quantity in grams");
        }
    }
}
