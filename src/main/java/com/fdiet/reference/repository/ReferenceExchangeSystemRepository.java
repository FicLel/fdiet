package com.fdiet.reference.repository;

import com.fdiet.reference.model.ReferenceExchangeSystem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceExchangeSystemRepository extends JpaRepository<ReferenceExchangeSystem, Long> {

    @EntityGraph(attributePaths = "source")
    List<ReferenceExchangeSystem> findAllByOrderByIdAsc();
}
