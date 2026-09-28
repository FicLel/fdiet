package com.fdiet.diet.dto;

import jakarta.validation.constraints.Size;

/**
 * What can change about a diet without sending its week again: its name, the
 * ration profile it is written against, whether it is clinical. A field left
 * out is left alone; a blank {@code referenceProfileCode} takes the profile off.
 *
 * <p>Changing the profile changes what the week is counted against and nothing
 * the patient reads: no gram is rewritten.
 */
public record DietSettingsDto(
        @Size(max = 255) String name,
        String referenceProfileCode,
        Boolean clinical) {
}
