package com.fdiet.diet.repository;

import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.DietStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Owns the {@code diets} table and the meal/dish/ingredient rows that hang off
 * it. The question it is asked most is the first one below: which diet is in
 * force right now.
 *
 * <p>The entity graph fetches the meals; the dishes, their ingredients and each
 * ingredient's food item follow in a handful of batched selects
 * ({@code hibernate.default_batch_fetch_size}) rather than one four-level join,
 * which list associations cannot be fetched through anyway.
 */
public interface DietRepository extends JpaRepository<DietPlan, Long> {

    /** The active diet, or empty while none has been set up. */
    @EntityGraph(attributePaths = "meals")
    Optional<DietPlan> findFirstByStatus(DietStatus status);

    @EntityGraph(attributePaths = "meals")
    Optional<DietPlan> findWithMealsById(Long id);

    /** The diets behind the active one, most recently started first. */
    Page<DietPlan> findByStatusNotOrderByStartedOnDesc(DietStatus status, Pageable pageable);
}
