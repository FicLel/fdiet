package com.fdiet.diet.service;

import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.food.dto.CompositionSuggestionDto;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.reference.domain.FoodState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngredientFoodServiceTest {

    private static final long LENTILS_DRY = 1L;
    private static final long LENTILS_BOILED = 2L;

    private final ICompositionFoodService compositionFoods = mock(ICompositionFoodService.class);
    private final IFoodResolverService resolver = mock(IFoodResolverService.class);
    private final IngredientFoodService service =
            new IngredientFoodService(compositionFoods, mock(IFoodItemService.class), resolver, 1);

    /** "lentejas cocidas" is offered the boiled lentils first, whatever the score said. */
    @Test
    void sinksASuggestionWhoseStateDisagreesWithTheText() {
        when(compositionFoods.suggest(anyString(), anyInt())).thenReturn(List.of(
                suggestion(LENTILS_DRY, "Lenteja, seca, cruda", 90),
                suggestion(LENTILS_BOILED, "Lenteja, hervida", 80)));

        assertThat(service.suggestionsFor("lentejas cocidas", FoodState.COOKED))
                .extracting(CompositionSuggestionDto::compositionFoodId)
                .containsExactly(LENTILS_BOILED);
    }

    /** By id when the caller gave one, by name otherwise; one batch each. */
    @Test
    void findsFoodsByIdAndByNameInBatches() {
        CompositionFood boiled = new CompositionFood();
        boiled.setId(LENTILS_BOILED);
        DishIngredient pinned = new DishIngredient("lentejas", new BigDecimal("60"), "g")
                .pinnedTo(LENTILS_BOILED, null);
        DishIngredient written = new DishIngredient("Lechuga", new BigDecimal("80"), "g");
        when(compositionFoods.entitiesByIds(Set.of(LENTILS_BOILED))).thenReturn(Map.of(LENTILS_BOILED, boiled));

        IngredientFoods foods = service.foodsOf(List.of(pinned, written));

        assertThat(foods.compositionFood(pinned)).isSameAs(boiled);
        verify(resolver).resolve(Set.of("Lechuga"));
    }

    @Test
    void refusesAnIdNothingCarries() {
        when(compositionFoods.entitiesByIds(anyCollection())).thenReturn(Map.of());
        DishIngredient made = new DishIngredient("x", BigDecimal.ONE, "g").pinnedTo(99L, null);

        assertThatThrownBy(() -> service.foodsOf(List.of(made)))
                .isInstanceOf(InvalidDietException.class)
                .hasMessageContaining("[99]");
    }

    private static CompositionSuggestionDto suggestion(long id, String name, int score) {
        return new CompositionSuggestionDto(id, name, CompositionSource.CIQUAL, "CIQUAL 2025", score);
    }
}
