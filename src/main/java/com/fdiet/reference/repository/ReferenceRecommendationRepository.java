package com.fdiet.reference.repository;

import com.fdiet.reference.model.ReferenceRecommendation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceRecommendationRepository extends JpaRepository<ReferenceRecommendation, Long> {

    @EntityGraph(attributePaths = {"population", "population.source"})
    List<ReferenceRecommendation> findAllByOrderByIdAsc();
}
