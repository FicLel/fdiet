package com.fdiet.reference.repository;

import com.fdiet.reference.model.ReferenceYieldFactor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Owns {@code ref_yield_factors}. Read whole into the reference snapshot. */
public interface ReferenceYieldFactorRepository extends JpaRepository<ReferenceYieldFactor, Long> {

    @EntityGraph(attributePaths = "source")
    List<ReferenceYieldFactor> findAllByOrderByIdAsc();
}
