package com.fdiet.diet.repository;

import com.fdiet.diet.model.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * The recipes, shared and private. Only the library is ever listed: a private
 * recipe is reached through the plate that holds it.
 *
 * <p>Names are compared by the column's collation, which is case- and
 * accent-insensitive — the way a person looking for "Crema de calabacín" reads
 * the list.
 */
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    Page<Recipe> findByLibraryTrueOrderByNameAsc(Pageable pageable);

    Page<Recipe> findByLibraryTrueAndNameContainingOrderByNameAsc(String name, Pageable pageable);

    Optional<Recipe> findFirstByLibraryTrueAndName(String name);
}
