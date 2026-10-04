package com.fdiet.journal.service;

import com.fdiet.diet.dto.DietProfileDto;
import com.fdiet.diet.helpers.IPortionScaler;
import com.fdiet.diet.service.IDietService;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.journal.mapper.IJournalMapper;
import com.fdiet.journal.model.ExtraFood;
import com.fdiet.journal.repository.DishScoreRepository;
import com.fdiet.journal.repository.ExtraFoodRepository;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.MeasureScope;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.dto.ScopedMeasureQueryDto;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FD-054: the extras a criterion reaches whose measure nobody picked are weighed
 * again inside their own diet; a diet's criterion reaches that diet's extras only.
 */
class JournalServiceReweighTest {

    private static final long EGG = 2127L;
    private static final String EGG_NAME = "Huevo, entero, crudo";
    private static final long DIET = 14L;
    private static final long OTHER_DIET = 15L;
    private static final String PROFILE = "AESAN-2022:ADULTOS";

    private final ExtraFoodRepository extraRepository = mock(ExtraFoodRepository.class);
    private final IDietService dietService = mock(IDietService.class);
    private final IReferenceService referenceService = mock(IReferenceService.class);

    private final JournalService journalService = new JournalService(
            mock(DishScoreRepository.class),
            extraRepository,
            mock(IJournalMapper.class),
            mock(IJournalNutritionService.class),
            dietService,
            mock(ICompositionFoodService.class),
            mock(IFoodItemService.class),
            referenceService,
            mock(IPortionScaler.class));

    private final ReferenceFoodMeasure criterion = measure(40L);
    private final ExtraFood mine = egg(DIET, "unidad");
    private final ExtraFood theirs = egg(OTHER_DIET, "unidad");
    private final ExtraFood spoonful = egg(DIET, "cucharada");

    @Test
    void reweighsOneDietsExtrasForItsCriterion() {
        when(extraRepository.findAutoMeasured(EGG)).thenReturn(List.of(mine, theirs, spoonful));
        when(dietService.profilesOf(List.of(DIET))).thenReturn(Map.of(DIET,
                new DietProfileDto(DIET, DIET, PROFILE)));
        when(referenceService.rechoose(anyList())).thenReturn(List.of(criterion));

        int reweighed = journalService.reweigh(new MeasureScope(EGG, HouseholdMeasure.UNIDAD, DIET));

        assertThat(reweighed).isEqualTo(1);
        assertThat(mine.getFoodMeasure()).isSameAs(criterion);
        assertThat(theirs.getFoodMeasure()).isNull();
        verify(referenceService).rechoose(List.of(new ScopedMeasureQueryDto(
                new MeasureQueryDto(EGG, EGG_NAME, "unidad", null, null), DIET, PROFILE)));
        verify(extraRepository).saveAll(List.of(mine));
    }

    /** The rule answering what the extra already holds is no re-weighing. */
    @Test
    void countsOnlyTheExtrasWhoseMeasureChanged() {
        mine.setFoodMeasure(criterion);
        when(extraRepository.findAutoMeasured(EGG)).thenReturn(List.of(mine, theirs));
        when(dietService.profilesOf(List.of(DIET, OTHER_DIET))).thenReturn(Map.of());
        when(referenceService.rechoose(anyList())).thenReturn(List.of(criterion, criterion));

        assertThat(journalService.reweigh(new MeasureScope(EGG, HouseholdMeasure.UNIDAD, null)))
                .isEqualTo(1);
        verify(extraRepository).saveAll(List.of(theirs));
    }

    private static ExtraFood egg(Long dietId, String unit) {
        CompositionFood egg = new CompositionFood();
        egg.setId(EGG);
        egg.setNameEs(EGG_NAME);
        return new ExtraFood(dietId, DayOfWeek.MONDAY, "huevo", BigDecimal.ONE, unit, egg, null);
    }

    private static ReferenceFoodMeasure measure(Long id) {
        ReferenceFoodMeasure row = new ReferenceFoodMeasure();
        row.setId(id);
        return row;
    }
}
