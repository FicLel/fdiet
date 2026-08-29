package com.fdiet.food.repository;

import com.fdiet.food.model.FoodItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long>, FoodItemBatchRepository {

    @EntityGraph(attributePaths = {"category", "subcategory"})
    @Override
    Page<FoodItem> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"category", "subcategory"})
    Page<FoodItem> findByCommercialNameContainingIgnoreCase(String name, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "subcategory"})
    Optional<FoodItem> findWithCategoriesById(Long id);

    @EntityGraph(attributePaths = {"category", "subcategory"})
    Optional<FoodItem> findByEan(String ean);

    /**
     * The rows carrying any of those commercial names, cheapest first. The
     * column's {@code utf8mb4_unicode_ci} collation is case- and
     * accent-insensitive, so {@code lechuga} finds {@code LECHUGA}.
     */
    List<FoodItem> findByCommercialNameInOrderByIdAsc(Collection<String> names);

    @Query("select f.ean from FoodItem f")
    List<String> findAllEans();
}
