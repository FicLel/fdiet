package com.fdiet.patient.dto;

import com.fdiet.patient.model.Sex;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A patient as they cross a layer boundary.
 *
 * <p>What they are eating is deliberately not here. A patient's diet belongs to
 * {@code com.fdiet.diet}, and {@code GET /api/diets/current} answers "who is on
 * a diet right now" in one query for everybody. Hanging it off this record
 * instead would make the patient context depend on the diet context, and the
 * arrow only runs the other way: a diet is written <em>for</em> somebody.
 *
 * <p>{@code birthDate} and {@code sex} are optional. Like every other field
 * here they are readable by anyone who opens the app — there is no security
 * layer — which was accepted for them explicitly.
 */
public record PatientDto(
        Long id,
        String name,
        String notes,
        LocalDate birthDate,
        Sex sex,
        LocalDateTime createdAt) {
}
