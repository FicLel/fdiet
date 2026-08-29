package com.fdiet.food.service;

import com.fdiet.food.dto.BedcaNameRow;
import com.fdiet.food.dto.FoodSuggestionDto;
import com.fdiet.food.helpers.NameMatcher;
import com.fdiet.food.mapper.IBedcaFoodMapper;
import com.fdiet.food.repository.BedcaFoodRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The candidate list behind the fix-up screen. Every ingredient text here is
 * taken from example-ui.xlsx, and every food name from bedca_foods.csv.
 */
class BedcaFoodServiceSuggestTest {

    private static final List<BedcaNameRow> CATALOGUE = List.of(
            new BedcaNameRow(2399L, "Lechuga", "Vegetables and vegetable products"),
            new BedcaNameRow(2422L, "Tomate, asado", null),
            new BedcaNameRow(2245L, "Plátano", "Fruit and fruit products"),
            new BedcaNameRow(994L, "Pollo, pechuga, con piel, crudo", null),
            new BedcaNameRow(2297L, "Pollo, pechuga, plancha", null),
            new BedcaNameRow(2296L, "Pollo, parte sin especificar", null),
            new BedcaNameRow(2216L, "Aguacate", "Fruit and fruit products"),
            new BedcaNameRow(1L, "Nuez", null));

    private final BedcaFoodRepository repository = mock(BedcaFoodRepository.class);
    private final BedcaFoodService service =
            new BedcaFoodService(repository, mock(IBedcaFoodMapper.class), new NameMatcher());

    @Test
    void putsTheFoodTheIngredientNamesInFull() {
        when(repository.findAllNames()).thenReturn(CATALOGUE);

        List<FoodSuggestionDto> suggestions = service.suggest("lechuga", 5);

        assertThat(suggestions.get(0).name()).isEqualTo("Lechuga");
        assertThat(suggestions.get(0).score()).isEqualTo(100);
        assertThat(suggestions.get(0).bedcaFoodId()).isEqualTo(2399L);
    }

    @Test
    void readsPastTheQuantityAndThePreparation() {
        when(repository.findAllNames()).thenReturn(CATALOGUE);

        assertThat(service.suggest("Pechuga de pollo a la plancha (180 gr)", 3))
                .extracting(FoodSuggestionDto::name)
                .startsWith("Pollo, pechuga, plancha");
    }

    @Test
    void foldsSpanishPluralsOntoTheNameTheCatalogueUses() {
        when(repository.findAllNames()).thenReturn(CATALOGUE);

        assertThat(service.suggest("10 nueces", 3))
                .extracting(FoodSuggestionDto::name).containsExactly("Nuez");
    }

    @Test
    void offersNothingWhenTheWordsShareNothing() {
        when(repository.findAllNames()).thenReturn(CATALOGUE);

        assertThat(service.suggest("hummus casero suave", 5)).isEmpty();
        assertThat(service.suggest("   ", 5)).isEmpty();
    }

    @Test
    void keepsToTheLimitAsked() {
        when(repository.findAllNames()).thenReturn(CATALOGUE);

        assertThat(service.suggest("pollo", 2)).hasSize(2);
        assertThat(service.suggest("pollo", 0)).isEmpty();
    }

    @Test
    void buildsTheIndexOnceAndAnswersFromMemoryAfterThat() {
        when(repository.findAllNames()).thenReturn(CATALOGUE);

        service.suggest("lechuga", 5);
        service.suggest("tomate", 5);
        service.suggest("aguacate", 5);

        // A page of unmatched ingredients must not cost a query each.
        verify(repository, times(1)).findAllNames();
    }
}
