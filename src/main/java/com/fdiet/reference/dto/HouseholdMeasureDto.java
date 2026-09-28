package com.fdiet.reference.dto;

import com.fdiet.reference.domain.HouseholdMeasure;

import java.util.List;

/** One kitchen word and the spellings the parser reads as it. */
public record HouseholdMeasureDto(
        HouseholdMeasure code,
        String label,
        List<String> aliases) {
}
