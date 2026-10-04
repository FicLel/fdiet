package com.fdiet.food.service;

import com.fdiet.food.dto.CompositionFoodDto;
import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionIndexRow;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionStoreResultDto;
import com.fdiet.food.dto.CompositionSuggestionDto;
import com.fdiet.food.helpers.NameMatcher;
import com.fdiet.food.mapper.ICompositionFoodMapper;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.repository.CompositionFoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Names here are the real ones: CIQUAL and BLS English and original names, and
 * the Spanish names of the crosswalk.
 */
class CompositionFoodServiceTest {

    private static final List<CompositionIndexRow> TABLE = List.of(
            new CompositionIndexRow(1L, CompositionSource.CIQUAL, "20031", "Lechuga, cruda", "lechuga",
                    true, "Lettuce, raw", "Laitue, crue"),
            new CompositionIndexRow(2L, CompositionSource.CIQUAL, "20288", null, null, false,
                    "Lettuce, sucrine, raw", "Salade sucrine, crue"),
            new CompositionIndexRow(3L, CompositionSource.CIQUAL, "17270", "Aceite de oliva virgen extra",
                    "aceite de oliva;AOVE", true, "Olive oil, extra virgin", "Huile d'olive vierge extra"),
            new CompositionIndexRow(4L, CompositionSource.BLS, "Q120000", "Aceite de oliva", "aceite de oliva",
                    false, "Olive oil", "Olivenöl"),
            new CompositionIndexRow(5L, CompositionSource.CIQUAL, "36018", "Pollo, pechuga, plancha",
                    "pechuga de pollo a la plancha", true, "Chicken, breast, without skin, grilled/pan-fried",
                    "Poulet, filet, sans peau, grillé/poêlé"));

    private final CompositionFoodRepository repository = mock(CompositionFoodRepository.class);
    private final ICompositionFoodMapper mapper = mock(ICompositionFoodMapper.class);
    private final CompositionFoodService service =
            new CompositionFoodService(repository, mapper, new NameMatcher(), 500);

    @BeforeEach
    void table() {
        when(repository.findAllIndexRows()).thenReturn(TABLE);
        when(repository.findAllById(anyIterable())).thenAnswer(call -> {
            Iterable<Long> ids = call.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false).map(CompositionFoodServiceTest::food).toList();
        });
        when(mapper.toDto(any())).thenAnswer(call -> {
            CompositionFood food = call.getArgument(0);
            return new CompositionFoodDto(food.getId(), null, null, null, null, null, null, null, null,
                    List.of(), false, false, null, null, false, Map.of(), null);
        });
    }

    @Test
    void findsAFoodByItsSpanishNameOrAnyAlias() {
        Map<String, CompositionFood> found =
                service.entitiesByName(List.of("Lechuga", "AOVE", "pechuga de pollo a la plancha", "tomate"));

        assertThat(found).containsOnlyKeys("lechuga", "aove", "pechuga de pollo a la plancha");
        assertThat(found.get("aove").getId()).isEqualTo(3L);
        assertThat(found.get("pechuga de pollo a la plancha").getId()).isEqualTo(5L);
    }

    /** "aceite de oliva" is claimed by a CIQUAL and a BLS row; the preferred one answers. */
    @Test
    void answersASharedNameWithThePreferredRow() {
        assertThat(service.entitiesByName(List.of("aceite de oliva")).get("aceite de oliva").getId())
                .isEqualTo(3L);
    }

    /** A reference row names a food by (source, code); the id is this database's. Unknown keys are absent. */
    @Test
    void resolvesStableKeysToIds() {
        CompositionKey lettuce = new CompositionKey(CompositionSource.CIQUAL, "20031");
        CompositionKey blsOil = new CompositionKey(CompositionSource.BLS, "Q120000");
        CompositionKey sameCodeOtherTable = new CompositionKey(CompositionSource.BLS, "20031");

        assertThat(service.idsByKey(List.of(lettuce, blsOil, sameCodeOtherTable)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(lettuce, 1L, blsOil, 4L));
        verify(repository, times(0)).findAllById(anyIterable());
    }

    /** The index is one query, however many lookups and searches follow. */
    @Test
    void buildsTheIndexOnce() {
        service.entitiesByName(List.of("lechuga"));
        service.entitiesByName(List.of("aove"));
        service.idsByKey(List.of(new CompositionKey(CompositionSource.CIQUAL, "20031")));
        service.search("lechuga", 0, 20);

        verify(repository, times(1)).findAllIndexRows();
    }

    /**
     * Spanish words first; a food without a Spanish name yet is still found by
     * its English or French name as typed, after them.
     */
    @Test
    void ranksSpanishNamesFirstThenAnyNameAsTyped() {
        assertThat(ids(service.search("lechuga", 0, 20))).containsExactly(1L);
        assertThat(ids(service.search("lettuce", 0, 20))).containsExactly(1L, 2L);
        assertThat(ids(service.search("Olivenöl", 0, 20))).containsExactly(4L);
    }

    /** Offers rank Spanish names only, best first, with each food's table; no query beyond the index. */
    @Test
    void suggestsCrosswalkedFoodsWithTheirSourceAndNeverAnUntranslatedOne() {
        List<CompositionSuggestionDto> offered = service.suggest("2 cucharadas de aceite de oliva", 5);

        assertThat(offered).extracting(CompositionSuggestionDto::compositionFoodId).containsExactly(4L, 3L);
        assertThat(offered.get(0).source()).isEqualTo(CompositionSource.BLS);
        assertThat(offered.get(0).sourceLabel()).isEqualTo("BLS 4.0");
        assertThat(service.suggest("lettuce", 5)).isEmpty();
        assertThat(service.suggest("lechuga", 0)).isEmpty();
        verify(repository, times(0)).findAllById(anyIterable());
    }

    @Test
    void countsWhatASyncInsertsAndWhatItWritesOver() {
        List<CompositionFoodRowDto> rows = List.of(
                row(CompositionSource.CIQUAL, "20031"),
                row(CompositionSource.BLS, "G130100"));

        CompositionStoreResultDto result = service.storeAll(rows);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1);
        verify(repository).upsertAll(eq(rows), eq(500));
    }

    /** After a sync the next lookup reads the table again, so a changed name is seen. */
    @Test
    void dropsTheIndexWhenASyncChangesTheTable() {
        service.entitiesByName(List.of("lechuga"));
        service.storeAll(List.of(row(CompositionSource.CIQUAL, "20031")));
        service.entitiesByName(List.of("lechuga"));

        // Once for the first index, once to tell new rows from stored ones, once for the new index.
        verify(repository, times(3)).findAllIndexRows();
        verify(repository).upsertAll(anyList(), eq(500));
    }

    /**
     * A search running while a sync's transaction is still open rebuilds the
     * index from the rows as they were; the end of the transaction drops it
     * again, so the next lookup sees what the sync committed.
     */
    @Test
    void dropsTheIndexAgainWhenTheSyncTransactionEnds() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.storeAll(List.of(row(CompositionSource.CIQUAL, "20031")));
            service.entitiesByName(List.of("lechuga"));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(
                            TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        service.entitiesByName(List.of("lechuga"));

        // Once to tell new rows from stored ones, once mid-transaction, once after it ended.
        verify(repository, times(3)).findAllIndexRows();
    }

    private static List<Long> ids(com.fdiet.common.dto.PageDto<CompositionFoodDto> page) {
        return page.content().stream().map(CompositionFoodDto::id).collect(Collectors.toCollection(ArrayList::new));
    }

    private static CompositionFood food(Long id) {
        CompositionFood food = new CompositionFood();
        food.setId(id);
        return food;
    }

    private static CompositionFoodRowDto row(CompositionSource source, String code) {
        return new CompositionFoodRowDto(new CompositionKey(source, code), "x", "x", null, Map.of(), null);
    }
}
