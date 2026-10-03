package com.fdiet.food.repository;

import com.fdiet.food.dto.CompositionIndexRow;
import com.fdiet.food.model.CompositionFood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompositionFoodRepository
        extends JpaRepository<CompositionFood, Long>, CompositionFoodUpsertRepository {

    /**
     * Names and keys only, for the in-memory search and name index, and for a
     * sync to tell new rows from stored ones. One select of every row.
     */
    @Query("select new com.fdiet.food.dto.CompositionIndexRow(f.id, f.source, f.sourceCode, f.nameEs, "
            + "f.nameAliases, f.namePreferred, f.nameEn, f.nameOriginal) from CompositionFood f")
    List<CompositionIndexRow> findAllIndexRows();
}
