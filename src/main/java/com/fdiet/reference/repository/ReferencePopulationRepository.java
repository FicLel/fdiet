package com.fdiet.reference.repository;

import com.fdiet.reference.model.ReferencePopulation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferencePopulationRepository extends JpaRepository<ReferencePopulation, Long> {

    @EntityGraph(attributePaths = "source")
    List<ReferencePopulation> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "source")
    Optional<ReferencePopulation> findByCode(String code);
}
