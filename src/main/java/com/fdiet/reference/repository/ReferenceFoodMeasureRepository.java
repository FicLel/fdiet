package com.fdiet.reference.repository;

import com.fdiet.reference.model.ReferenceFoodMeasure;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceFoodMeasureRepository extends JpaRepository<ReferenceFoodMeasure, Long> {

    /** The published rows, held in memory by the service: every weighing reads them. */
    @EntityGraph(attributePaths = "source")
    List<ReferenceFoodMeasure> findByDietIdIsNullOrderByIdAsc();

    /** One diet's own rows. */
    List<ReferenceFoodMeasure> findByDietIdOrderByIdAsc(Long dietId);
}
