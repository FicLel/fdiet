package com.fdiet.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A patient as they are written down: a name, and whatever the nutritionist
 * wants to remember about them.
 *
 * <p>The name is the whole identity while there are no accounts, so it is
 * required and no two patients may share one.
 */
public record PatientRequestDto(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String notes) {
}
