package com.fdiet.diet.repository;

import com.fdiet.diet.model.PlannedIngredient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * The fix-up path: an ingredient nobody could match to a food is listed on its
 * own and corrected on its own, without loading the diet it belongs to.
 * Everything else about an ingredient is written through the cascade on
 * {@link com.fdiet.diet.model.DietPlan}.
 *
 * <p>Every query is reached from the diet id, so an ingredient of one diet can
 * never be read or corrected through the URL of another.
 *
 * <p>"Matched" means either half of the catalogue answered, which is a
 * two-column condition and so is written out rather than derived from a method
 * name.
 */
public interface PlannedIngredientRepository extends JpaRepository<PlannedIngredient, Long> {

    @EntityGraph(attributePaths = {"foodItem", "bedcaFood"})
    Page<PlannedIngredient> findByDishMealDietId(Long dietId, Pageable pageable);

    /** The ones still waiting to be matched — the fix-up list. */
    @EntityGraph(attributePaths = {"foodItem", "bedcaFood"})
    @Query("select i from PlannedIngredient i where i.dish.meal.diet.id = :dietId "
            + "and i.foodItem is null and i.bedcaFood is null")
    Page<PlannedIngredient> findUnmatched(@Param("dietId") Long dietId, Pageable pageable);

    @EntityGraph(attributePaths = {"foodItem", "bedcaFood"})
    @Query("select i from PlannedIngredient i where i.dish.meal.diet.id = :dietId "
            + "and (i.foodItem is not null or i.bedcaFood is not null)")
    Page<PlannedIngredient> findMatched(@Param("dietId") Long dietId, Pageable pageable);

    Optional<PlannedIngredient> findByIdAndDishMealDietId(Long id, Long dietId);
}
