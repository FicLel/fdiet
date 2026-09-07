package com.fdiet.patient.repository;

import com.fdiet.patient.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Owns the {@code patients} table. There are a handful of rows in it — a
 * nutritionist's caseload, not a user base — so it is read whole and ordered by
 * name rather than paged.
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    List<Patient> findAllByOrderByNameAsc();

    /**
     * The one already holding that name, if there is one. The collation is case-
     * and accent-insensitive, so this answers the question a person asks looking
     * at the list: is there already a Victor.
     */
    Optional<Patient> findByName(String name);
}
