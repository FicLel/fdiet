package com.fdiet.alternative.dto;

import java.math.BigDecimal;

/**
 * A portion read as rations of the reference profile's group for that food —
 * "≈ 1,2-1,6 raciones de cereales, AESAN 2022". A ration defined as a range
 * divides into a range, and is sent as one.
 *
 * <p>Absent (null where it would sit) when the profile counts the food in no
 * group, when the ration is a bare count with no weight, or when the ration is
 * defined in another state than the food is published in — a boiled rice is not
 * counted against a dry-rice ration, for the same reason the week is not.
 */
public record RationEquivalentDto(
        String groupCode,
        String groupLabel,
        BigDecimal rationsMin,
        BigDecimal rationsMax,
        String sourceShortName,
        String pageRef) {
}
