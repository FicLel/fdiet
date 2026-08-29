package com.fdiet.diet.service;

import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.service.IBedcaFoodService;
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

    private final IBedcaFoodService bedcaFoodService = mock(IBedcaFoodService.class);
    private final IFoodItemService foodItemService = mock(IFoodItemService.class);
    private final FoodResolverService resolver =
            new FoodResolverService(bedcaFoodService, foodItemService, 100);

    @Test
    void asksTheCompositionDatabaseOnceForTheWholeWeek() {
        when(bedcaFoodService.entitiesByName(anyCollection()))
                .thenReturn(Map.of("lechuga", generic(7L, "Lechuga")));

        Map<String, FoodMatch> resolved =
                resolver.resolve(List.of("Lechuga", "lechuga ", "tomate", "lechuga"));

        assertThat(resolved).containsOnlyKeys("lechuga");
        assertThat(resolved.get("lechuga").bedcaFood().getId()).isEqualTo(7L);
        // One query, and the repeated name was asked for once.
        verify(bedcaFoodService, times(1)).entitiesByName(List.of("lechuga", "tomate"));
        verify(bedcaFoodService, never()).entitiesByIds(anyCollection());
    }

    @Test
    void putsOnlyTheNamesTheCompositionDatabaseMissedToTheBrandedCatalogue() {
        when(bedcaFoodService.entitiesByName(anyCollection()))
                .thenReturn(Map.of("lechuga", generic(7L, "Lechuga")));
        when(foodItemService.entitiesByName(anyCollection()))
                .thenReturn(Map.of("bekind barra cereal", branded(31L)));

        Map<String, FoodMatch> resolved =
                resolver.resolve(List.of("lechuga", "bekind barra cereal"));

        assertThat(resolved.get("lechuga").bedcaFood().getId()).isEqualTo(7L);
        assertThat(resolved.get("lechuga").foodItem()).isNull();
        assertThat(resolved.get("bekind barra cereal").foodItem().getId()).isEqualTo(31L);
        // "lechuga" was already answered, so it never reaches the branded catalogue.
        verify(foodItemService, times(1)).entitiesByName(List.of("bekind barra cereal"));
    }

    @Test
    void goesToTheCacheTheSecondTimeInsteadOfSearchingByNameAgain() {
        when(bedcaFoodService.entitiesByName(anyCollection()))
                .thenReturn(Map.of("lechuga", generic(7L, "Lechuga")));
        when(bedcaFoodService.entitiesByIds(anyCollection()))
                .thenReturn(Map.of(7L, generic(7L, "Lechuga")));

        resolver.resolve(List.of("lechuga"));
        Map<String, FoodMatch> again = resolver.resolve(List.of("Lechuga"));

        assertThat(again).containsOnlyKeys("lechuga");
        verify(bedcaFoodService, times(1)).entitiesByName(anyCollection());

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.captor();
        verify(bedcaFoodService, times(1)).entitiesByIds(ids.capture());
        assertThat(ids.getValue()).containsExactly(7L);
    }

    @Test
    void leavesOutTheNamesNeitherHalfCarries() {
        when(bedcaFoodService.entitiesByName(anyCollection())).thenReturn(Map.of());
        when(foodItemService.entitiesByName(anyCollection())).thenReturn(Map.of());

        assertThat(resolver.resolve(List.of("hummus casero suave"))).isEmpty();
    }

    @Test
    void forgetsAFoodThatIsNoLongerStored() {
        when(bedcaFoodService.entitiesByName(anyCollection()))
                .thenReturn(Map.of("lechuga", generic(7L, "Lechuga")));
        when(bedcaFoodService.entitiesByIds(anyCollection())).thenReturn(Map.of());
        when(foodItemService.entitiesByName(anyCollection())).thenReturn(Map.of());

        resolver.resolve(List.of("lechuga"));
        assertThat(resolver.resolve(List.of("lechuga"))).isEmpty();

        // The stale id was dropped, so the third run searches by name again.
        resolver.resolve(List.of("lechuga"));
        verify(bedcaFoodService, times(2)).entitiesByName(anyCollection());
    }

    @Test
    void ignoresBlankNamesRatherThanQueryingForThem() {
        assertThat(resolver.resolve(List.of("  ", ""))).isEmpty();
        verify(bedcaFoodService, never()).entitiesByName(any());
        verify(foodItemService, never()).entitiesByName(any());
    }

    private static BedcaFood generic(Long id, String name) {
        BedcaFood food = new BedcaFood();
        food.setId(id);
        food.setName(name);
        return food;
    }

    private static FoodItem branded(Long id) {
        FoodItem item = new FoodItem();
        item.setId(id);
        item.setCommercialName("BEKIND BARRA CEREAL");
        return item;
    }
}
