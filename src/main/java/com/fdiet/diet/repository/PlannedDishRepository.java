package com.fdiet.diet.repository;

import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.model.PlannedDish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;

/**
 * Dishes are written through the cascade on
 * {@link com.fdiet.diet.model.DietPlan} and read with their week, so this
 * repository answers only the one question nothing else can: whether a place in
 * the week is actually filled.
 *
 * <p>The journal asks it before recording an opinion of a plate. A score is
 * kept against the slot rather than the dish row — the row is replaced on every
 * publish — so without this check a mistyped request would store a score of a
 * plate that does not exist, and nothing would ever read it to notice.
 */
public interface PlannedDishRepository extends JpaRepository<PlannedDish, Long> {

    @Query("select count(d) > 0 from PlannedDish d "
            + "where d.meal.diet.id = :dietId "
            + "and d.meal.dayOfWeek = :day "
            + "and d.meal.type = :mealType "
            + "and d.position = :dishIndex")
    boolean existsAtSlot(@Param("dietId") Long dietId,
                         @Param("day") DayOfWeek day,
                         @Param("mealType") MealType mealType,
                         @Param("dishIndex") int dishIndex);
}
