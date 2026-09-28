package com.fdiet.reference.repository;

import com.fdiet.reference.model.ReferenceMealShare;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceMealShareRepository extends JpaRepository<ReferenceMealShare, Long> {

    @EntityGraph(attributePaths = {"population", "population.source"})
    List<ReferenceMealShare> findAllByOrderByIdAsc();
}
