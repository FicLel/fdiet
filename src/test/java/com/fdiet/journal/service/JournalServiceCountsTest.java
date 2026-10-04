package com.fdiet.journal.service;

import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.helpers.IPortionScaler;
import com.fdiet.diet.service.IDietService;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.journal.dto.JournalCountsDto;
import com.fdiet.journal.mapper.IJournalMapper;
import com.fdiet.journal.repository.DishScoreRepository;
import com.fdiet.journal.repository.ExtraFoodRepository;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a diet's journal holds, counted for the confirm a diet delete shows: one
 * count per table, never the rows themselves.
 */
class JournalServiceCountsTest {

    private static final Long DIET_ID = 14L;

    private final DishScoreRepository scoreRepository = mock(DishScoreRepository.class);
    private final ExtraFoodRepository extraRepository = mock(ExtraFoodRepository.class);
    private final IDietService dietService = mock(IDietService.class);

    private final JournalService journalService = new JournalService(
            scoreRepository,
            extraRepository,
            mock(IJournalMapper.class),
            mock(IJournalNutritionService.class),
            dietService,
            mock(ICompositionFoodService.class),
            mock(IFoodItemService.class),
            mock(IReferenceService.class),
            mock(IPortionScaler.class));

    @Test
    void countsScoresAndExtrasWithoutReadingThem() {
        when(dietService.exists(DIET_ID)).thenReturn(true);
        when(scoreRepository.countByDietId(DIET_ID)).thenReturn(5L);
        when(extraRepository.countByDietId(DIET_ID)).thenReturn(2L);

        JournalCountsDto counts = journalService.counts(DIET_ID);

        assertThat(counts).isEqualTo(new JournalCountsDto(DIET_ID, 5, 2));
        verify(scoreRepository, never()).findByDietId(any());
        verify(extraRepository, never()).findByDietIdOrderByLoggedAtAsc(any());
    }

    @Test
    void anUnknownDietIsNotFound() {
        when(dietService.exists(DIET_ID)).thenReturn(false);

        assertThatThrownBy(() -> journalService.counts(DIET_ID))
                .isInstanceOf(DietNotFoundException.class);
    }
}
