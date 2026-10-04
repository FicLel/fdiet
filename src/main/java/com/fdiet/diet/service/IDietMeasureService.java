package com.fdiet.diet.service;

import com.fdiet.diet.dto.DietMeasureSavedDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureCriterionRequestDto;

import java.util.List;

/**
 * One diet's own household-measure weights — the nutritionist's criterion for
 * that diet — reached through the diet's URL. The rows are the reference
 * module's; this checks the diet and forwards.
 */
public interface IDietMeasureService {

    /** The diet's own household-measure weights. */
    List<FoodMeasureDto> measures(Long dietId);

    /**
     * Writes the diet's own weight for a measure; every ingredient and extra of the
     * diet it reaches whose measure nobody picked is chosen again (FD-054).
     */
    DietMeasureSavedDto saveMeasure(Long dietId, MeasureCriterionRequestDto request);

    /** Removes one of the diet's own measures; the ingredients it weighed are left unmeasured. */
    void deleteMeasure(Long dietId, Long measureId);
}
