package com.fdiet.reference.service;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto;
import com.fdiet.reference.exception.InvalidReferenceException;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.LicenceClass;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.dto.ScopedMeasureQueryDto;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.helpers.ReferenceMatcher;
import com.fdiet.reference.mapper.ReferenceMapper;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.model.ReferenceSource;
import com.fdiet.reference.repository.ReferenceExchangeSystemRepository;
import com.fdiet.reference.repository.ReferenceFoodMeasureRepository;
import com.fdiet.reference.repository.ReferenceMealShareRepository;
import com.fdiet.reference.repository.ReferencePopulationRepository;
import com.fdiet.reference.repository.ReferenceRationRepository;
import com.fdiet.reference.repository.ReferenceRecommendationRepository;
import com.fdiet.reference.repository.ReferenceSourceRepository;
import com.fdiet.reference.repository.ReferenceYieldFactorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Where the reference service meets the nutritionist's global criteria: they
 * weigh with no diet at all — an unsaved week, a library recipe — and the sync
 * of the published rows neither reads them as published nor writes them.
 *
 * <p>Both name a composition food (FD-033 phase C), and since phase D every
 * matched ingredient carries one, so both reach it by id. A food known only by a
 * name reaches the family rows and never a row or criterion naming a food.
 */
class ReferenceServiceCriterionTest {

    /** The egg as a composition food. */
    private static final long EGG = 2127L;
    private static final long DIET_ID = 5L;
    private static final CompositionKey EGG_KEY = new CompositionKey(CompositionSource.CIQUAL, "22000");
    private static final CompositionKey UNLOADED_KEY = new CompositionKey(CompositionSource.BLS, "Z999999");
    private static final String EGG_NAME = "Huevo, entero, crudo";
    private static final String AESAN = "AESAN-2022-007";
    private static final String RANGE_CODE = "AESAN-2022:M-HUEVO-UD";

    private final ReferenceSourceRepository sources = mock(ReferenceSourceRepository.class);
    private final ReferencePopulationRepository populations = mock(ReferencePopulationRepository.class);
    private final ReferenceRationRepository rations = mock(ReferenceRationRepository.class);
    private final ReferenceFoodMeasureRepository measures = mock(ReferenceFoodMeasureRepository.class);
    private final ReferenceRecommendationRepository recommendations =
            mock(ReferenceRecommendationRepository.class);
    private final ReferenceMealShareRepository shares = mock(ReferenceMealShareRepository.class);
    private final ReferenceExchangeSystemRepository exchanges = mock(ReferenceExchangeSystemRepository.class);
    private final ReferenceYieldFactorRepository yields = mock(ReferenceYieldFactorRepository.class);
    private final IFoodCategoriser categoriser = mock(IFoodCategoriser.class);
    private final ICompositionFoodService compositionFoodService = mock(ICompositionFoodService.class);
    private final IMeasureCriterionService criteria = mock(IMeasureCriterionService.class);
    private final ReferenceMapper mapper = new ReferenceMapper();

    private final ReferenceService service = new ReferenceService(
            new ReferenceTables(sources, populations, rations, measures, recommendations, shares,
                    exchanges, yields, mapper, compositionFoodService),
            mapper, new ReferenceMatcher(), categoriser, compositionFoodService,
            criteria, "AESAN-2022:ADULTOS");

    private final FoodMeasureDto global = globalEgg();

    @BeforeEach
    void stubs() {
        when(categoriser.of(anyString())).thenReturn(FoodCategory.EGG);
        when(measures.findByDietIdIsNullAndGlobalCriterionFalseOrderByIdAsc())
                .thenReturn(List.of(publishedRange()));
        when(criteria.globalRows(anyCollection()))
                .thenAnswer(call -> call.<Collection<Long>>getArgument(0).contains(EGG)
                        ? List.of(global) : List.of());
        when(compositionFoodService.idsByKey(anyCollection())).thenReturn(Map.of(EGG_KEY, EGG));
        CompositionFood egg = new CompositionFood();
        egg.setId(EGG);
        egg.setNameEs(EGG_NAME);
        when(compositionFoodService.entityById(EGG)).thenReturn(egg);
    }

    /** No diet — an unsaved week, or a library recipe — and the egg still weighs, by her criterion. */
    @Test
    void theGlobalCriterionWeighsWithoutAnyDiet() {
        MeasureChoiceDto choice = service.chooseMeasures(List.of(eggs()), null, null).get(0);

        assertThat(choice.chosen()).isEqualTo(global);
        verify(criteria, never()).dietRows(any());
    }

    /** The composer lists it beside the published range, labelled as hers. */
    @Test
    void listsTheGlobalCriterionForAFoodAheadOfThePublishedRange() {
        List<FoodMeasureDto> listed = service.measuresForFood(EGG, "unidad", null, null);

        assertThat(listed).extracting(FoodMeasureDto::globalOwn).containsExactly(true, false);
        assertThat(listed.get(1).code()).isEqualTo(RANGE_CODE);
    }

    /**
     * A food known by its name only is weighed by family rows only: her criterion
     * names a composition food, and nothing but that food's id reaches it. With no
     * composition food asked about, the diet's criteria are not even read — every
     * one of them names a food.
     */
    @Test
    void aFoodKnownByNameOnlyIsNeverWeighedByACriterionNamingAFood() {
        MeasureChoiceDto choice = service.chooseMeasures(List.of(new MeasureQueryDto(null, EGG_NAME,
                "unidades", PortionSize.MEDIUM, null)), DIET_ID, null).get(0);

        assertThat(choice.chosen()).isNull();
        assertThat(choice.candidates()).extracting(FoodMeasureDto::code).containsExactly(RANGE_CODE);
        verify(criteria, never()).dietRows(any());
    }

    /** FD-033 D: an ingredient now carries its composition food, so the diet's own criterion reaches it. */
    @Test
    void theDietsOwnCriterionWeighsTheFoodItNames() {
        FoodMeasureDto own = dietEgg();
        when(criteria.dietRows(DIET_ID)).thenReturn(List.of(own));

        MeasureChoiceDto choice = service.chooseMeasures(List.of(eggs()), DIET_ID, null).get(0);

        assertThat(choice.chosen()).isEqualTo(own);
        assertThat(service.measuresForFood(EGG, "unidad", DIET_ID, null))
                .extracting(FoodMeasureDto::dietOwn).startsWith(true);
    }

    @Test
    void refusesALookupNamingNoFood() {
        assertThatThrownBy(() -> service.measuresForFood(null, "unidad", null, null))
                .isInstanceOf(InvalidReferenceException.class);
        assertThatThrownBy(() -> service.yieldFactorsForFood(null))
                .isInstanceOf(InvalidReferenceException.class);
    }

    /** One batched lookup keys the rows; a row whose food is not loaded is skipped with a reason. */
    @Test
    void aSyncKeysRowsByTheirCompositionFoodAndSkipsOneNotLoaded() {
        stubSaveAll();
        ReferenceRowsDto rows = syncRows();
        ReferenceRowsDto.FoodMeasure range = rows.foodMeasures().get(0);
        ReferenceRowsDto keyed = new ReferenceRowsDto(rows.sources(), List.of(), List.of(),
                List.of(withFood(range, "M-HUEVO-CIQUAL", EGG_KEY),
                        withFood(range, "M-HUEVO-UNLOADED", UNLOADED_KEY)),
                List.of(), List.of(), List.of(), List.of());

        ReferenceSyncSummaryDto summary = service.store(keyed);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReferenceFoodMeasure>> written = ArgumentCaptor.forClass(List.class);
        verify(measures).saveAll(written.capture());
        assertThat(written.getValue()).singleElement().satisfies(row -> {
            assertThat(row.getCode()).isEqualTo("M-HUEVO-CIQUAL");
            assertThat(row.getCompositionFoodId()).isEqualTo(EGG);
        });
        assertThat(summary.skipped()).singleElement().asString()
                .contains("M-HUEVO-UNLOADED").contains("BLS Z999999").contains("/api/composition/sync");
        verify(compositionFoodService).idsByKey(anyCollection());
    }

    private static ReferenceRowsDto.FoodMeasure withFood(ReferenceRowsDto.FoodMeasure row, String code,
                                                         CompositionKey food) {
        return new ReferenceRowsDto.FoodMeasure(row.origin(), code, row.sourceCode(), row.measure(),
                row.size(), row.count(), food, null, null, row.foodLabel(), new BigDecimal("58"),
                new BigDecimal("58"), null, null, row.state(), row.weightBasis(), null,
                row.householdText(), row.pageRef(), null);
    }

    /** A re-sync writes the published rows by code; the criterion is neither read nor written. */
    @Test
    void aResyncNeverWritesOrRemovesAGlobalCriterion() {
        stubSaveAll();

        service.store(syncRows());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReferenceFoodMeasure>> written = ArgumentCaptor.forClass(List.class);
        verify(measures).saveAll(written.capture());
        assertThat(written.getValue()).extracting(ReferenceFoodMeasure::getCode).containsExactly(RANGE_CODE);
        assertThat(written.getValue()).noneMatch(ReferenceFoodMeasure::isGlobalCriterion);
        verify(measures, never()).findByGlobalCriterionTrueOrderByFoodLabelAscIdAsc();
        verify(measures, never()).delete(any());
        verify(measures, never()).deleteAll(any());
        verify(measures, never()).deleteAllInBatch();
        verify(criteria, never()).deleteGlobal(any());
        verify(criteria, never()).updateGlobal(any(), any());

        // The snapshot is dropped by the sync and read again; her criterion still decides.
        assertThat(service.chooseMeasures(List.of(eggs()), null, null).get(0).chosen()).isEqualTo(global);
    }

    private void stubSaveAll() {
        for (JpaRepository<?, ?> repository : List.<JpaRepository<?, ?>>of(sources, populations, rations,
                measures, recommendations, shares, exchanges, yields)) {
            when(repository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        }
    }

    private static ReferenceRowsDto syncRows() {
        ReferenceRowsDto.Source source = new ReferenceRowsDto.Source("sources.csv:2", AESAN, "AESAN 2022",
                "Informe", "AESAN", "ES", 1, 2022, null, LicenceClass.A, "reuse", "AESAN", false, null,
                null);
        ReferenceRowsDto.FoodMeasure range = new ReferenceRowsDto.FoodMeasure("food_measures.csv:2",
                RANGE_CODE, AESAN, HouseholdMeasure.UNIDAD, PortionSize.MEDIUM, BigDecimal.ONE, null,
                FoodCategory.EGG, "huevo", "Huevo mediano", new BigDecimal("53"), new BigDecimal("63"),
                null, null, FoodState.UNSPECIFIED, WeightBasis.UNSPECIFIED, null,
                "1 huevo mediano (53-63 g)", "p. 53", null);
        return new ReferenceRowsDto(List.of(source), List.of(), List.of(), List.of(range), List.of(),
                List.of(), List.of(), List.of());
    }

    /**
     * FD-054: rows of many diets re-chosen in one batch — each inside its own diet,
     * so the diet's criterion wins in its diet and the global one in a library
     * recipe — reading every diet's criteria in one query.
     */
    @Test
    void rechoosesRowsOfManyDietsEachInsideItsOwnDiet() {
        when(criteria.dietRowsOf(anyCollection())).thenReturn(Map.of(DIET_ID, List.of(dietEgg())));
        ReferenceFoodMeasure own = new ReferenceFoodMeasure();
        own.setId(41L);
        ReferenceFoodMeasure hers = new ReferenceFoodMeasure();
        hers.setId(40L);
        when(measures.findAllById(anyCollection())).thenReturn(List.of(own, hers));

        List<ReferenceFoodMeasure> chosen = service.rechoose(List.of(
                new ScopedMeasureQueryDto(eggs(), DIET_ID, null),
                new ScopedMeasureQueryDto(eggs(), null, null)));

        assertThat(chosen).containsExactly(own, hers);
        verify(criteria).dietRowsOf(java.util.Set.of(DIET_ID));
        verify(criteria, never()).dietRows(any());
    }

    private static MeasureQueryDto eggs() {
        return new MeasureQueryDto(EGG, EGG_NAME, "unidades", PortionSize.MEDIUM, null);
    }

    private static ReferenceFoodMeasure publishedRange() {
        ReferenceSource source = new ReferenceSource();
        source.setCode(AESAN);
        source.setShortName("AESAN 2022");
        source.setTier(1);
        ReferenceFoodMeasure row = new ReferenceFoodMeasure();
        row.setId(1L);
        row.setCode(RANGE_CODE);
        row.setSource(source);
        row.setMeasure(HouseholdMeasure.UNIDAD);
        row.setSize(PortionSize.MEDIUM);
        row.setCount(BigDecimal.ONE);
        row.setFoodCategory(FoodCategory.EGG);
        row.setKeywords("huevo");
        row.setFoodLabel("Huevo mediano");
        row.setGramsMin(new BigDecimal("53"));
        row.setGramsMax(new BigDecimal("63"));
        row.setState(FoodState.UNSPECIFIED);
        row.setWeightBasis(WeightBasis.UNSPECIFIED);
        return row;
    }

    /** The diet's own egg: 60 g a medium unit, ahead of her global 58 g. */
    private static FoodMeasureDto dietEgg() {
        BigDecimal grams = new BigDecimal("60");
        return new FoodMeasureDto(41L, null, HouseholdMeasure.UNIDAD, "unidad", PortionSize.MEDIUM,
                BigDecimal.ONE, EGG_NAME, EGG, FoodCategory.EGG, null, grams, grams, null, null, grams,
                FoodState.UNSPECIFIED, WeightBasis.NET_EDIBLE, null, "1 unidad mediana", null, null, null,
                null, null, DIET_ID, true, false);
    }

    private static FoodMeasureDto globalEgg() {
        BigDecimal grams = new BigDecimal("58");
        return new FoodMeasureDto(40L, null, HouseholdMeasure.UNIDAD, "unidad", PortionSize.MEDIUM,
                BigDecimal.ONE, EGG_NAME, EGG, FoodCategory.EGG, null, grams, grams, null, null, grams,
                FoodState.UNSPECIFIED, WeightBasis.NET_EDIBLE, null, "1 unidad mediana", null, null, null,
                null, null, null, false, true);
    }
}
