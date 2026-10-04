package com.fdiet.diet.repository;

import com.fdiet.diet.dto.DietProfileDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.model.PlannedDish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;

/**
 * Dishes are written through the cascade on
 * {@link com.fdiet.diet.model.DietPlan} and read with their week, so this
 * repository answers only what nothing else can: which recipes a diet serves,
 * and whether a place in the week is actually filled.
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

    /**
     * The recipes this diet's plates serve — the set its ingredients are read and
     * corrected through, so a URL naming one diet never reaches another's.
     */
    @Query("select distinct d.recipe.id from PlannedDish d "
            + "where d.meal.diet.id = :dietId and d.recipe is not null")
    List<Long> recipeIdsOf(@Param("dietId") Long dietId);

    /**
     * The diet each of these private recipes is served in — one plate each — with
     * that diet's profile, keyed by the recipe. Library recipes are left out: they
     * are weighed without any diet's criteria.
     */
    @Query("select new com.fdiet.diet.dto.DietProfileDto(d.recipe.id, m.diet.id, m.diet.referenceProfileCode) "
            + "from PlannedDish d join d.meal m "
            + "where d.recipe.id in :recipeIds and d.recipe.library = false")
    List<DietProfileDto> dietsServingPrivate(@Param("recipeIds") Collection<Long> recipeIds);

    /** How many plates, in any diet, serve this recipe. */
    long countByRecipeId(Long recipeId);
}
