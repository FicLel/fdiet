package com.fdiet.journal.repository;

import com.fdiet.diet.dto.MealType;
import com.fdiet.journal.model.DishScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

/**
 * Owns {@code dish_scores}. A week's scores are read whole — there are at most
 * seventy of them — and written one slot at a time.
 */
public interface DishScoreRepository extends JpaRepository<DishScore, Long> {

    List<DishScore> findByDietId(Long dietId);

    long countByDietId(Long dietId);

    /** The one score of a slot, which {@code uk_dish_scores_slot} keeps unique. */
    Optional<DishScore> findByDietIdAndDayOfWeekAndMealTypeAndDishIndex(
            Long dietId, DayOfWeek dayOfWeek, MealType mealType, int dishIndex);
}
