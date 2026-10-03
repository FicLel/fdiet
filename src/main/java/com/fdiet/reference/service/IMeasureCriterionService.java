package com.fdiet.reference.service;

import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureCriterionRequestDto;
import com.fdiet.reference.dto.MeasureUsageDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The nutritionist's own weights for household measures: one diet's criteria
 * and her global ones, the rows of {@code ref_food_measures} no source
 * published. The published rows stay with {@link IReferenceService}, which
 * reads both kinds from here when it chooses a measure.
 *
 * <p>Precedence, applied by the matcher: a measure a person picked, then the
 * diet's criterion, then the global criterion, then published rows.
 */
public interface IMeasureCriterionService {

    /** One diet's own criteria. One query. */
    List<FoodMeasureDto> dietRows(Long dietId);

    /** The global criteria for these foods. One query, none for an empty set. */
    List<FoodMeasureDto> globalRows(Collection<Long> bedcaFoodIds);

    /**
     * Writes the diet's criterion for one measure of one food (and size),
     * replacing an earlier one for the same measure, food and size. The caller
     * has checked the diet exists.
     */
    FoodMeasureDto saveDietMeasure(Long dietId, MeasureCriterionRequestDto request);

    void deleteDietMeasure(Long dietId, Long measureId);

    /** A diet's criteria written again for another diet: old id to new id. */
    Map<Long, Long> copyDietMeasures(Long fromDietId, Long toDietId);

    /** Every global criterion, or only one food's when {@code bedcaFoodId} is given. */
    List<FoodMeasureDto> globalCriteria(Long bedcaFoodId);

    FoodMeasureDto globalCriterion(Long id);

    /** A new global criterion. One already held for the same food, measure and size is a 400. */
    FoodMeasureDto createGlobal(MeasureCriterionRequestDto request);

    /**
     * Rewrites a global criterion — live for everything it weighs. While it
     * weighs anything its food, measure and size are fixed: moving it would
     * weigh those rows as another food.
     */
    FoodMeasureDto updateGlobal(Long id, MeasureCriterionRequestDto request);

    /** Deletes a global criterion nothing is weighed by; one still in use is a 400. */
    void deleteGlobal(Long id);

    /** What a global criterion weighs now: the reach of a change to it. */
    MeasureUsageDto usage(Long id);
}
