package com.fdiet.reference.dto;

/**
 * A population band of one source, which a diet can be written against when it
 * is {@code selectable}. The label is the source's own; the ages are only what
 * the source stated.
 *
 * @param suggested whether this is the profile suggested for the age the caller
 *                  asked about — an offer, never applied on its own
 */
public record ReferenceProfileDto(
        String code,
        String label,
        String sourceCode,
        String sourceShortName,
        Integer ageMinMonths,
        Integer ageMaxMonths,
        String context,
        boolean selectable,
        boolean suggested) {
}
