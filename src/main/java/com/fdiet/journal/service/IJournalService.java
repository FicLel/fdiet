package com.fdiet.journal.service;

import com.fdiet.diet.dto.MealType;
import com.fdiet.journal.dto.DietJournalDto;
import com.fdiet.journal.dto.DishScoreDto;
import com.fdiet.journal.dto.ExtraFoodDto;
import com.fdiet.journal.dto.JournalCountsDto;
import com.fdiet.journal.dto.LogExtraFoodRequestDto;
import com.fdiet.journal.dto.ScoreDishRequestDto;

import java.time.DayOfWeek;

/**
 * Owns {@code dish_scores} and {@code extra_foods} — the two tables the patient
 * writes. It reaches the week through {@link com.fdiet.diet.service.IDietService}
 * and the catalogue through the food module's services, never through their
 * repositories.
 */
public interface IJournalService {

    /** A whole week's scores and off-plan entries, in one answer. */
    DietJournalDto find(Long dietId);

    /**
     * How many scores and extras the diet holds, counted in the database — what
     * deleting the diet would take with it.
     */
    JournalCountsDto counts(Long dietId);

    /**
     * Sets what the patient thought of one plate, writing over any earlier
     * opinion of the same slot.
     */
    DishScoreDto score(Long dietId,
                       DayOfWeek day,
                       MealType mealType,
                       int dishIndex,
                       ScoreDishRequestDto request);

    /**
     * Takes a score back. Not a score of zero — "no opinion" must stay out of
     * the average rather than drag it down.
     */
    void clearScore(Long dietId, DayOfWeek day, MealType mealType, int dishIndex);

    /** Records something eaten that the plan did not prescribe. */
    ExtraFoodDto logExtra(Long dietId, LogExtraFoodRequestDto request);

    void removeExtra(Long dietId, Long extraId);
}
