package com.fdiet.diet.service;

import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.food.dto.CompositionSuggestionDto;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.reference.domain.FoodState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IngredientFoodService implements IIngredientFoodService {

    /** How many more candidates are ranked than shown, so a state disagreement can sink. */
    private static final int SUGGESTION_POOL = 3;

    private final ICompositionFoodService compositionFoodService;
    private final IFoodItemService foodItemService;
    private final IFoodResolverService foodResolverService;
    private final int suggestionLimit;

    public IngredientFoodService(ICompositionFoodService compositionFoodService,
                                 IFoodItemService foodItemService,
                                 IFoodResolverService foodResolverService,
                                 @Value("${fdiet.diet.suggestion-limit:5}") int suggestionLimit) {
        this.compositionFoodService = compositionFoodService;
        this.foodItemService = foodItemService;
        this.foodResolverService = foodResolverService;
        this.suggestionLimit = suggestionLimit;
    }

    /** O(n) over the ingredients; two id lookups and the resolver's batched calls. */
    @Override
    @Transactional(readOnly = true)
    public IngredientFoods foodsOf(List<DishIngredient> ingredients) {
        Set<Long> itemIds = new LinkedHashSet<>();
        Set<Long> genericIds = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (DishIngredient ingredient : ingredients) {
            if (ingredient.compositionFoodId() != null) {
                genericIds.add(ingredient.compositionFoodId());
            } else if (ingredient.foodItemId() != null) {
                itemIds.add(ingredient.foodItemId());
            } else {
                names.add(ingredient.name());
            }
        }

        Map<Long, FoodItem> items = foodItemService.entitiesByIds(itemIds);
        requireAllFound(itemIds, items.keySet(), "food items");
        Map<Long, CompositionFood> generic = compositionFoodService.entitiesByIds(genericIds);
        requireAllFound(genericIds, generic.keySet(), "CIQUAL or BLS foods");

        return new IngredientFoods(items, generic, foodResolverService.resolve(names));
    }

    /**
     * {@code lentejas cocidas} is offered {@code Lenteja, hervida} before
     * {@code Lenteja, seca, cruda}. A stable sort of a short pool: O(k log k).
     */
    @Override
    public List<CompositionSuggestionDto> suggestionsFor(String name, FoodState written) {
        List<CompositionSuggestionDto> ordered = new ArrayList<>(
                compositionFoodService.suggest(name, suggestionLimit * SUGGESTION_POOL));
        ordered.sort(Comparator.comparing((CompositionSuggestionDto suggestion) ->
                FoodState.disagree(written, FoodState.ofFoodName(suggestion.name()))));
        return ordered.stream().limit(suggestionLimit).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CompositionFood compositionFood(Long id) {
        return compositionFoodService.entityById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodItem foodItem(Long id) {
        return foodItemService.entityById(id);
    }

    /** An id the caller made up is a mistake to report, not a food to guess at. */
    private static void requireAllFound(Set<Long> asked, Set<Long> found, String what) {
        List<Long> unknown = asked.stream().filter(id -> !found.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw new InvalidDietException("Unknown " + what + ": " + unknown);
        }
    }
}
