package com.fdiet.reference.repository;

import com.fdiet.reference.model.ReferenceFoodMeasure;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * The three kinds of measure row: published, one diet's criterion, and the
 * nutritionist's global criterion. {@code ReferenceService} owns the published
 * ones and {@code MeasureCriterionService} the nutritionist's two kinds.
 */
public interface ReferenceFoodMeasureRepository extends JpaRepository<ReferenceFoodMeasure, Long> {

    /** The published rows, held in memory by the service: every weighing reads them. */
    @EntityGraph(attributePaths = "source")
    List<ReferenceFoodMeasure> findByDietIdIsNullAndGlobalCriterionFalseOrderByIdAsc();

    /** One diet's own rows. */
    List<ReferenceFoodMeasure> findByDietIdOrderByIdAsc(Long dietId);

    /** The nutritionist's global criteria for these foods. */
    List<ReferenceFoodMeasure> findByGlobalCriterionTrueAndCompositionFoodIdInOrderByIdAsc(
            Collection<Long> compositionFoodIds);

    /** Every global criterion, by food. */
    List<ReferenceFoodMeasure> findByGlobalCriterionTrueOrderByFoodLabelAscIdAsc();
}
