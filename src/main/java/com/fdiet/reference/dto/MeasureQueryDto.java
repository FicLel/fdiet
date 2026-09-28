package com.fdiet.reference.dto;

import com.fdiet.reference.domain.PortionSize;

/**
 * One written ingredient that may need a household measure to be weighed: the
 * composition-database food it was matched to, the unit and size it was written
 * in, and the measure a caller already chose for it, if any.
 */
public record MeasureQueryDto(
        Long bedcaFoodId,
        String foodName,
        String unit,
        PortionSize size,
        Long preferredMeasureId) {
}
