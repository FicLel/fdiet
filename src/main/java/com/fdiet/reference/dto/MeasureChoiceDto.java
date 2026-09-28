package com.fdiet.reference.dto;

import java.util.List;

/**
 * The household measures that could weigh one ingredient, and the one attached,
 * if any.
 *
 * <p>{@code chosen} is set only when the choice is not a judgement: the one a
 * person already picked, the diet's own criterion, the only row the diet's
 * profile source publishes, or several sources that agree to the gram.
 * Otherwise it is null and {@code candidates} is the list a person picks from —
 * the same rule food matching follows.
 */
public record MeasureChoiceDto(
        FoodMeasureDto chosen,
        List<FoodMeasureDto> candidates) {

    public static final MeasureChoiceDto NONE = new MeasureChoiceDto(null, List.of());
}
