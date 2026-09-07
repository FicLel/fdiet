package com.fdiet.patient.service;

import com.fdiet.patient.dto.PatientDto;
import com.fdiet.patient.dto.PatientRequestDto;
import com.fdiet.patient.model.Patient;

import java.util.List;

/**
 * Owns the {@code patients} table.
 *
 * <p>It knows nothing about diets. A diet points at its patient, so
 * {@code com.fdiet.diet} calls in here for the row it needs and this context
 * never calls back — which is also why {@link #entityById} exists: a diet has to
 * hold the entity to write the foreign key, and a service handing an entity to
 * another service is one layer talking to itself.
 */
public interface IPatientService {

    /** Everybody, by name. A caseload is a handful of rows, so it is not paged. */
    List<PatientDto> findAll();

    PatientDto findById(Long id);

    /** Adds a patient. The name must not be one somebody already holds. */
    PatientDto create(PatientRequestDto request);

    /** Renames a patient, or rewrites the note kept about them. */
    PatientDto update(Long id, PatientRequestDto request);

    /**
     * Removes a patient who has no diets. One who has is refused rather than
     * quietly taking their weeks and their journal with them.
     */
    void delete(Long id);

    /** The stored row, for the diet service that has to point a diet at it. */
    Patient entityById(Long id);
}
