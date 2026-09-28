package com.fdiet.reference.repository;

import com.fdiet.reference.model.ReferenceRation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceRationRepository extends JpaRepository<ReferenceRation, Long> {

    /** Every ration with its population and source: the table is a few hundred rows, read whole. */
    @EntityGraph(attributePaths = {"population", "population.source"})
    List<ReferenceRation> findAllByOrderByIdAsc();
}
