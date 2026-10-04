package com.fdiet.reference.dto;

import com.fdiet.reference.domain.PortionSize;

/**
 * One written ingredient that may need a household measure to be weighed: the
 * food it was matched to, the unit and size it was written in, and the measure a
 * caller already chose for it, if any.
 *
 * <p>The food is a composition food (CIQUAL 2025 or BLS 4.0) by id, which is
 * what a reference row and every measure criterion names, and its Spanish name,
 * which is what a family row's keywords and the food's family are read from.
 * The name may be missing: a composition food the crosswalk names in no Spanish
 * reaches only the rows naming it.
 */
public record MeasureQueryDto(
        Long compositionFoodId,
        String foodName,
        String unit,
        PortionSize size,
        Long preferredMeasureId) {

    /** Whether there is anything to look a measure up by. */
    public boolean namesAFood() {
        return compositionFoodId != null || foodName != null;
    }
}
