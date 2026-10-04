package com.fdiet.diet.repository;

import com.fdiet.diet.model.RecipeIngredient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The fix-up path: an ingredient nobody could match to a food is listed on its
 * own and corrected on its own, without loading the recipe it belongs to.
 * Everything else about an ingredient is written through the cascade on
 * {@link com.fdiet.diet.model.Recipe}.
 *
 * <p>Every query is reached from a set of recipe ids — the ones a diet's plates
 * serve, which the diet service works out — so an ingredient of one diet can
 * never be read or corrected through the URL of another.
 *
 * <p>"Matched" means either half of the catalogue answered, which is a
 * two-column condition and so is written out rather than derived from a method
 * name.
 */
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    @EntityGraph(attributePaths = {"foodItem", "compositionFood"})
    Page<RecipeIngredient> findByRecipeIdIn(Collection<Long> recipeIds, Pageable pageable);

    @EntityGraph(attributePaths = {"compositionFood", "recipe"})
    List<RecipeIngredient> findByRecipeIdIn(Collection<Long> recipeIds);

    /** The ones still waiting to be matched — the fix-up list. */
    @EntityGraph(attributePaths = {"foodItem", "compositionFood"})
    @Query("select i from RecipeIngredient i where i.recipe.id in :recipeIds "
            + "and i.foodItem is null and i.compositionFood is null")
    Page<RecipeIngredient> findUnmatched(@Param("recipeIds") Collection<Long> recipeIds,
                                         Pageable pageable);

    @EntityGraph(attributePaths = {"foodItem", "compositionFood"})
    @Query("select i from RecipeIngredient i where i.recipe.id in :recipeIds "
            + "and (i.foodItem is not null or i.compositionFood is not null)")
    Page<RecipeIngredient> findMatched(@Param("recipeIds") Collection<Long> recipeIds,
                                       Pageable pageable);

    @EntityGraph(attributePaths = "recipe")
    Optional<RecipeIngredient> findByIdAndRecipeIdIn(Long id, Collection<Long> recipeIds);

    /** How many ingredients, of any recipe, a household measure weighs — the reach of a change to it. */
    long countByFoodMeasureId(Long foodMeasureId);
}
