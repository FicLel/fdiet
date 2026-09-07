package com.fdiet.patient.dto;

import java.time.LocalDateTime;

/**
 * A patient as they cross a layer boundary.
 *
 * <p>What they are eating is deliberately not here. A patient's diet belongs to
 * {@code com.fdiet.diet}, and {@code GET /api/diets/current} answers "who is on
 * a diet right now" in one query for everybody. Hanging it off this record
 * instead would make the patient context depend on the diet context, and the
 * arrow only runs the other way: a diet is written <em>for</em> somebody.
 */
public record PatientDto(
        Long id,
        String name,
        String notes,
        LocalDateTime createdAt) {
}
