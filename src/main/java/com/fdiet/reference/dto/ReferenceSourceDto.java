package com.fdiet.reference.dto;

import com.fdiet.reference.domain.LicenceClass;

import java.time.LocalDate;

/**
 * A document reference figures were read from. {@code attribution} is the line
 * a screen showing any of its figures has to show beside them.
 */
public record ReferenceSourceDto(
        String code,
        String shortName,
        String title,
        String institution,
        String country,
        int tier,
        Integer year,
        String url,
        LicenceClass licenceClass,
        String licence,
        String attribution,
        boolean clinical,
        LocalDate retrievedOn,
        String notes) {
}
