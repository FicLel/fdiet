package com.fdiet.reference.repository;

import com.fdiet.reference.model.ReferenceSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceSourceRepository extends JpaRepository<ReferenceSource, Long> {

    List<ReferenceSource> findAllByOrderByTierAscYearDesc();
}
