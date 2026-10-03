package com.fdiet.reference.dto;

import com.fdiet.reference.domain.PortionSize;

/**
 * One written ingredient that may need a household measure to be weighed: the
 * food it was matched to, the unit and size it was written in, and the measure a
 * caller already chose for it, if any.
 *
 * <p>The food is a composition food (CIQUAL 2025 or BLS 4.0) by id, which is
 * what a reference row names, and a Spanish name, which is what a family row's
 * keywords and the food's family are read from. Either may be missing: a
 * composition food without a Spanish name reaches only the rows naming it, and
 * an ingredient matched to a BEDCA food — every ingredient until FD-033 phase D
 * re-matches them — reaches only the family rows, through {@link #byNameOnly}.
 * A BEDCA id is never compared with a composition id.
 */
public record MeasureQueryDto(
        Long compositionFoodId,
        String foodName,
        String unit,
        PortionSize size,
        Long preferredMeasureId) {

    /**
     * A food known by its name only — today, a BEDCA-matched ingredient. It is
     * weighed by family and keyword rows and by none that names a food, until
     * FD-033 phase D gives the ingredient a composition food.
     */
    public static MeasureQueryDto byNameOnly(String foodName, String unit, PortionSize size,
                                             Long preferredMeasureId) {
        return new MeasureQueryDto(null, foodName, unit, size, preferredMeasureId);
    }

    /** Whether there is anything to look a measure up by. */
    public boolean namesAFood() {
        return compositionFoodId != null || foodName != null;
    }
}
