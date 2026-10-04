package com.fdiet.diet.service;

import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.IFoodItemService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodResolverServiceTest {

    private final ICompositionFoodService compositionFoodService = mock(ICompositionFoodService.class);
    private final IFoodItemService foodItemService = mock(IFoodItemService.class);
    private final FoodResolverService resolver =
            new FoodResolverService(compositionFoodService, foodItemService, 100);

    @Test
    void asksTheCompositionFoodsOnceForTheWholeWeek() {
        when(compositionFoodService.entitiesByName(anyCollection()))
                .thenReturn(Map.of("lechuga", generic(7L, "Lechuga")));

        Map<String, FoodMatch> resolved =
                resolver.resolve(List.of("Lechuga", "lechuga ", "tomate", "lechuga"));

        assertThat(resolved).containsOnlyKeys("lechuga");
        assertThat(resolved.get("lechuga").compositionFood().getId()).isEqualTo(7L);
        // One lookup by name, and the repeated name was asked for once.
        verify(compositionFoodService, times(1)).entitiesByName(List.of("lechuga", "tomate"));
    }

    /** "kiwi mediano" is a kiwi: the size says how big the piece is, never which food. */
    @Test
    void triesANameOnceMoreWithoutItsSizeWords() {
        when(compositionFoodService.entitiesByName(List.of("kiwi mediano"))).thenReturn(Map.of());
        when(compositionFoodService.entitiesByName(List.of("kiwi")))
                .thenReturn(Map.of("kiwi", generic(9L, "Kiwi")));

        Map<String, FoodMatch> resolved = resolver.resolve(List.of("kiwi mediano"));

        assertThat(resolved.get("kiwi mediano").compositionFood().getId()).isEqualTo(9L);
        verify(foodItemService, never()).entitiesByName(any());
    }

    @Test
    void putsOnlyTheNamesTheCompositionFoodsMissedToTheBrandedCatalogue() {
        when(compositionFoodService.entitiesByName(anyCollection()))
                .thenReturn(Map.of("lechuga", generic(7L, "Lechuga")));
        when(foodItemService.entitiesByName(anyCollection()))
                .thenReturn(Map.of("bekind barra cereal", branded(31L)));

        Map<String, FoodMatch> resolved =
                resolver.resolve(List.of("lechuga", "bekind barra cereal"));

        assertThat(resolved.get("lechuga").compositionFood().getId()).isEqualTo(7L);
        assertThat(resolved.get("lechuga").foodItem()).isNull();
        assertThat(resolved.get("bekind barra cereal").foodItem().getId()).isEqualTo(31L);
        // "lechuga" was already answered, so it never reaches the branded catalogue.
        verify(foodItemService, times(1)).entitiesByName(List.of("bekind barra cereal"));
    }

    @Test
    void goesToTheBrandedCacheTheSecondTimeInsteadOfSearchingByNameAgain() {
        when(compositionFoodService.entitiesByName(anyCollection())).thenReturn(Map.of());
        when(foodItemService.entitiesByName(anyCollection()))
                .thenReturn(Map.of("bekind barra cereal", branded(31L)));
        when(foodItemService.entitiesByIds(anyCollection())).thenReturn(Map.of(31L, branded(31L)));

        resolver.resolve(List.of("bekind barra cereal"));
        Map<String, FoodMatch> again = resolver.resolve(List.of("BEKIND barra cereal"));

        assertThat(again).containsOnlyKeys("bekind barra cereal");
        verify(foodItemService, times(1)).entitiesByName(anyCollection());
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.captor();
        verify(foodItemService, times(1)).entitiesByIds(ids.capture());
        assertThat(ids.getValue()).containsExactly(31L);
    }

    @Test
    void leavesOutTheNamesNeitherHalfCarries() {
        when(compositionFoodService.entitiesByName(anyCollection())).thenReturn(Map.of());
        when(foodItemService.entitiesByName(anyCollection())).thenReturn(Map.of());

        assertThat(resolver.resolve(List.of("hummus casero suave"))).isEmpty();
    }

    @Test
    void ignoresBlankNamesRatherThanQueryingForThem() {
        assertThat(resolver.resolve(List.of("  ", ""))).isEmpty();
        verify(compositionFoodService, never()).entitiesByName(any());
        verify(foodItemService, never()).entitiesByName(any());
    }

    private static CompositionFood generic(Long id, String name) {
        CompositionFood food = new CompositionFood();
        food.setId(id);
        food.setNameEs(name);
        return food;
    }

    private static FoodItem branded(Long id) {
        FoodItem item = new FoodItem();
        item.setId(id);
        item.setCommercialName("BEKIND BARRA CEREAL");
        return item;
    }
}
