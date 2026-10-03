package com.fdiet.food.service;

import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.dto.CompositionStoreResultDto;
import com.fdiet.food.dto.CompositionSyncSummaryDto;
import com.fdiet.food.dto.CompositionTableDto;
import com.fdiet.food.dto.NutrientDto;
import com.fdiet.food.exception.InvalidCompositionDataException;
import com.fdiet.food.helpers.ICompositionLinkReader;
import com.fdiet.food.helpers.ICompositionTableReader;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.model.Nutrient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositionImportServiceTest {

    private static final CompositionKey LETTUCE = new CompositionKey(CompositionSource.CIQUAL, "20031");
    private static final CompositionKey MUSHROOM = new CompositionKey(CompositionSource.CIQUAL, "20161");
    private static final CompositionKey ROCKET = new CompositionKey(CompositionSource.BLS, "G130100");

    private final ICompositionTableReader ciqual = reader(CompositionSource.CIQUAL,
            row(LETTUCE, true), row(MUSHROOM, false));
    private final ICompositionTableReader bls = reader(CompositionSource.BLS, row(ROCKET, true));
    private final ICompositionLinkReader linkReader = mock(ICompositionLinkReader.class);
    private final ICompositionFoodService store = mock(ICompositionFoodService.class);
    private final CompositionImportService service =
            new CompositionImportService(List.of(bls, ciqual), linkReader, store, "data");

    @Test
    void namesTheCrosswalkedFoodsAndStoresEveryFoodOnce() {
        when(linkReader.read(any())).thenReturn(List.of(
                link(LETTUCE, "Lechuga, cruda", List.of("lechuga"), true),
                link(ROCKET, "Rúcula, cruda", List.of("rúcula"), true)));
        when(store.storeAll(anyList())).thenReturn(new CompositionStoreResultDto(3, 0));

        CompositionSyncSummaryDto summary = service.sync();

        Map<CompositionKey, CompositionFoodRowDto> stored = captured();
        assertThat(stored).containsOnlyKeys(LETTUCE, MUSHROOM, ROCKET);
        assertThat(stored.get(LETTUCE).link().nameEs()).isEqualTo("Lechuga, cruda");
        assertThat(stored.get(ROCKET).link().nameEs()).isEqualTo("Rúcula, cruda");
        assertThat(stored.get(MUSHROOM).link()).isNull();
        assertThat(summary.ciqualRows()).isEqualTo(2);
        assertThat(summary.blsRows()).isEqualTo(1);
        assertThat(summary.linked()).isEqualTo(2);
        assertThat(summary.withoutEnergy()).isEqualTo(1);
        assertThat(summary.inserted()).isEqualTo(3);
        assertThat(summary.attributions()).hasSize(2);
    }

    /** Skipped with its reason, the way the reference sync skips a row naming an unknown food. */
    @Test
    void reportsACrosswalkRowNamingACodeNeitherTableHolds() {
        CompositionKey ghost = new CompositionKey(CompositionSource.CIQUAL, "99999");
        when(linkReader.read(any())).thenReturn(List.of(link(ghost, "Fantasma", List.of(), true)));
        when(store.storeAll(anyList())).thenReturn(new CompositionStoreResultDto(3, 0));

        CompositionSyncSummaryDto summary = service.sync();

        assertThat(summary.linked()).isZero();
        assertThat(summary.linksUnmatched()).singleElement().asString().contains("CIQUAL 99999", "Fantasma");
    }

    /** Two foods for "tomate" and neither preferred: nothing is written. */
    @Test
    void refusesACrosswalkThatGivesANameToTwoFoodsWithoutAPreference() {
        when(linkReader.read(any())).thenReturn(List.of(
                link(LETTUCE, "Lechuga, cruda", List.of("ensalada"), false),
                link(ROCKET, "Rúcula, cruda", List.of("ensalada"), false)));

        assertThatThrownBy(service::sync)
                .isInstanceOf(InvalidCompositionDataException.class)
                .hasMessageContaining("ensalada");
        verify(store, never()).storeAll(anyList());
    }

    @Test
    void refusesACrosswalkListingAFoodTwice() {
        when(linkReader.read(any())).thenReturn(List.of(
                link(LETTUCE, "Lechuga, cruda", List.of(), true),
                link(LETTUCE, "Lechuga", List.of(), true)));

        assertThatThrownBy(service::sync)
                .isInstanceOf(InvalidCompositionDataException.class)
                .hasMessageContaining("CIQUAL 20031");
        verify(store, never()).storeAll(anyList());
    }

    @SuppressWarnings("unchecked")
    private Map<CompositionKey, CompositionFoodRowDto> captured() {
        ArgumentCaptor<List<CompositionFoodRowDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).storeAll(captor.capture());
        return captor.getValue().stream().collect(java.util.stream.Collectors.toMap(
                CompositionFoodRowDto::key, java.util.function.Function.identity()));
    }

    private static ICompositionTableReader reader(CompositionSource source, CompositionFoodRowDto... rows) {
        ICompositionTableReader reader = mock(ICompositionTableReader.class);
        when(reader.source()).thenReturn(source);
        when(reader.read(any(Path.class))).thenReturn(new CompositionTableDto(List.of(rows), 0));
        return reader;
    }

    private static CompositionFoodRowDto row(CompositionKey key, boolean withEnergy) {
        Map<Nutrient, NutrientDto> nutrients = withEnergy
                ? Map.of(Nutrient.ENERGY, new NutrientDto(new BigDecimal("15"), "kcal"))
                : Map.of(Nutrient.PROTEIN, new NutrientDto(new BigDecimal("3"), "g"));
        return new CompositionFoodRowDto(key, "original " + key, "english " + key, null, nutrients, null);
    }

    private static CompositionLinkDto link(CompositionKey key, String nameEs, List<String> aliases,
                                           boolean preferred) {
        return new CompositionLinkDto(key, nameEs, aliases, preferred, null, null, false);
    }
}
