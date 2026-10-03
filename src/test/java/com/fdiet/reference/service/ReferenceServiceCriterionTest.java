package com.fdiet.reference.service;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.LicenceClass;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
 */
class ReferenceServiceCriterionTest {

    private static final long EGG = 2127L;
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
    private final IBedcaFoodService bedcaFoodService = mock(IBedcaFoodService.class);
    private final IMeasureCriterionService criteria = mock(IMeasureCriterionService.class);
    private final ReferenceMapper mapper = new ReferenceMapper();

    private final ReferenceService service = new ReferenceService(sources, populations, rations,
            measures, recommendations, shares, exchanges, yields, mapper, new ReferenceMatcher(),
            categoriser, bedcaFoodService, criteria, "AESAN-2022:ADULTOS");

    private final FoodMeasureDto global = globalEgg();

    @BeforeEach
    void stubs() {
        when(categoriser.of(anyString())).thenReturn(FoodCategory.EGG);
        when(measures.findByDietIdIsNullAndGlobalCriterionFalseOrderByIdAsc())
                .thenReturn(List.of(publishedRange()));
        when(criteria.globalRows(anyCollection())).thenReturn(List.of(global));
        when(bedcaFoodService.entitiesByIds(any())).thenReturn(Map.of());
        BedcaFood egg = new BedcaFood();
        egg.setId(EGG);
        egg.setName(EGG_NAME);
        when(bedcaFoodService.entityById(EGG)).thenReturn(egg);
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

    private static FoodMeasureDto globalEgg() {
        BigDecimal grams = new BigDecimal("58");
        return new FoodMeasureDto(40L, null, HouseholdMeasure.UNIDAD, "unidad", PortionSize.MEDIUM,
                BigDecimal.ONE, EGG_NAME, EGG, FoodCategory.EGG, null, grams, grams, null, null, grams,
                FoodState.UNSPECIFIED, WeightBasis.NET_EDIBLE, null, "1 unidad mediana", null, null, null,
                null, null, null, false, true);
    }
}
