package com.fdiet.diet.repository;

import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.DietStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Owns the {@code diets} table and the meal/dish/ingredient rows that hang off
 * it. The question it is asked most is the first one below: which diet is in
 * force right now <em>for this patient</em>. Every one of these is scoped by
 * patient except the two that address a diet by its own id — an id is already
 * one person's.
 *
 * <p>The entity graph fetches the meals; the dishes, their ingredients and each
 * ingredient's food item follow in a handful of batched selects
 * ({@code hibernate.default_batch_fetch_size}) rather than one four-level join,
 * which list associations cannot be fetched through anyway. {@code patient} is
 * fetched with them because every one of these rows is read back saying whose
 * it is, and a lazy proxy per row would be a query per row.
 */
public interface DietRepository extends JpaRepository<DietPlan, Long> {

    /** The patient's active diet, or empty while they have not been given one. */
    @EntityGraph(attributePaths = {"meals", "patient"})
    Optional<DietPlan> findFirstByPatientIdAndStatus(Long patientId, DietStatus status);

    @EntityGraph(attributePaths = {"meals", "patient"})
    Optional<DietPlan> findWithMealsById(Long id);

    /** The diets behind this patient's active one, most recently started first. */
    @EntityGraph(attributePaths = "patient")
    Page<DietPlan> findByPatientIdAndStatusNotOrderByStartedOnDesc(
            Long patientId, DietStatus status, Pageable pageable);

    /**
     * Every patient's diet in force, in one query — the board the patient
     * selector is drawn from. Without their weeks: this answers who is on a
     * diet, not what is in it.
     */
    @EntityGraph(attributePaths = "patient")
    List<DietPlan> findByStatusOrderByPatientNameAsc(DietStatus status);

    /** Every diet with a plate serving this recipe, most recently started first. */
    @EntityGraph(attributePaths = "patient")
    @Query("select distinct p from DietPlan p join p.meals m join m.dishes d "
            + "where d.recipe.id = :recipeId order by p.startedOn desc")
    List<DietPlan> findServingRecipe(@Param("recipeId") Long recipeId);

    /**
     * Deletes one diet in a single statement. The schema takes its meals, dishes,
     * scores, extras and own measure criteria with it ({@code ON DELETE CASCADE}),
     * so nothing is loaded to be removed row by row.
     *
     * @return how many diets were deleted: 0 when the id names none
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DietPlan p where p.id = :id")
    int deleteWithWeekById(@Param("id") Long id);
}
