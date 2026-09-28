package com.fdiet.patient.dto;

import com.fdiet.patient.model.Sex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A patient as they are written down: a name, and whatever the nutritionist
 * wants to remember about them.
 *
 * <p>The name is the whole identity while there are no accounts, so it is
 * required and no two patients may share one. The birth date and sex are
 * optional; a PUT that leaves them out clears them, as it does the note.
 */
public record PatientRequestDto(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String notes,
        @PastOrPresent LocalDate birthDate,
        Sex sex) {
}
