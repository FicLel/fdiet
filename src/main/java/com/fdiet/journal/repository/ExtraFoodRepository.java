package com.fdiet.journal.repository;

import com.fdiet.journal.model.ExtraFood;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

    @EntityGraph(attributePaths = {"bedcaFood", "foodItem", "foodMeasure"})
    List<ExtraFood> findByDietIdOrderByLoggedAtAsc(Long dietId);

    @EntityGraph(attributePaths = {"bedcaFood", "foodItem", "foodMeasure"})
    Optional<ExtraFood> findWithFoodById(Long id);

    long countByDietId(Long dietId);

    /** How many extras a household measure weighs — the reach of a change to it. */
    long countByFoodMeasureId(Long foodMeasureId);
}
