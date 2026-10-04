package com.fdiet.diet.service;

import com.fdiet.diet.dto.DietMeasureSavedDto;
import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureCriterionRequestDto;
import com.fdiet.reference.dto.MeasureCriterionSavedDto;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns no table: the criteria are {@code ref_food_measures} rows the reference
 * module writes, and re-weighing what a criterion reaches is done by the owners
 * of those rows through {@code IMeasureReweigher} — the recipe ingredients by
 * {@link RecipeMeasureReweigher}, the extras by the journal. Split off
 * {@code DietService}, which is about the week.
 */
@Service
public class DietMeasureService implements IDietMeasureService {

    private final IDietService dietService;
    private final IReferenceService referenceService;

    public DietMeasureService(IDietService dietService, IReferenceService referenceService) {
        this.dietService = dietService;
        this.referenceService = referenceService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodMeasureDto> measures(Long dietId) {
        requireDiet(dietId);
        return referenceService.dietMeasures(dietId);
    }

    /**
     * A library recipe is left alone: it is shared, and one diet's criterion is
     * not everybody's. {@code attached} is the total re-weighed, kept beside the
     * breakdown for callers that read it before FD-054.
     */
    @Override
    @Transactional
    public DietMeasureSavedDto saveMeasure(Long dietId, MeasureCriterionRequestDto request) {
        requireDiet(dietId);
        MeasureCriterionSavedDto saved = referenceService.saveDietMeasure(dietId, request);
        return new DietMeasureSavedDto(saved.measure(), (int) saved.reweighed().total(),
                saved.reweighed());
    }

    @Override
    @Transactional
    public void deleteMeasure(Long dietId, Long measureId) {
        requireDiet(dietId);
        referenceService.deleteDietMeasure(dietId, measureId);
    }

    private void requireDiet(Long dietId) {
        if (!dietService.exists(dietId)) {
            throw DietNotFoundException.diet(dietId);
        }
    }
}
