package com.fdiet.journal.repository;

import com.fdiet.journal.model.ExtraFood;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Owns {@code extra_foods}.
 *
 * <p>The entity graph fetches both catalogue references, and the household
 * measure that weighs an entry, with the rows. Their
 * composition figures are what an entry is read for, and without it a week of
 * extras would be a select each.
 */
public interface ExtraFoodRepository extends JpaRepository<ExtraFood, Long> {

    @EntityGraph(attributePaths = {"compositionFood", "foodItem", "foodMeasure"})
    List<ExtraFood> findByDietIdOrderByLoggedAtAsc(Long dietId);

    @EntityGraph(attributePaths = {"compositionFood", "foodItem", "foodMeasure"})
    Optional<ExtraFood> findWithFoodById(Long id);

    long countByDietId(Long dietId);

    /**
     * The extras matched to one composition food whose measure no person picked,
     * with their measures: what a criterion for that food may re-weigh (FD-054).
     */
    @EntityGraph(attributePaths = {"compositionFood", "foodMeasure"})
    @Query("select x from ExtraFood x where x.compositionFood.id = :foodId "
            + "and (x.measurePicked = false or x.foodMeasure is null)")
    List<ExtraFood> findAutoMeasured(@Param("foodId") Long compositionFoodId);

    /** How many extras a household measure weighs — the reach of a change to it. */
    long countByFoodMeasureId(Long foodMeasureId);
}
