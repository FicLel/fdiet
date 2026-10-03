package com.fdiet.food.repository;

import com.fdiet.food.dto.BedcaNameRow;
import com.fdiet.food.model.BedcaFood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface BedcaFoodRepository extends JpaRepository<BedcaFood, Long> {

    /**
     * The foods carrying any of those names. The column collates case- and
     * accent-insensitively, so {@code lechuga} finds {@code Lechuga}.
     */
    List<BedcaFood> findByNameInOrderByIdAsc(Collection<String> names);

    /** Names only, for the in-memory suggestion index. */
    @Query("select new com.fdiet.food.dto.BedcaNameRow(f.id, f.name, f.foodGroup) from BedcaFood f")
    List<BedcaNameRow> findAllNames();
}
