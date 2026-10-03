package com.fdiet.reference.controller;

import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureCriterionRequestDto;
import com.fdiet.reference.dto.MeasureUsageDto;
import com.fdiet.reference.service.IMeasureCriterionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Web layer for the nutritionist's global measure criteria. It forwards to the
 * service and returns what it hands back.
 *
 * <p>The list is not paged: it is one person's criteria, a handful per food she
 * writes in units, and every weighing reads them anyway.
 */
@RestController
@RequestMapping("/api/reference/criteria")
@Tag(name = "Measure criteria controller",
        description = "The nutritionist's own weight for a household measure of a food, for every diet")
public class MeasureCriterionController {

    private final IMeasureCriterionService criterionService;

    public MeasureCriterionController(IMeasureCriterionService criterionService) {
        this.criterionService = criterionService;
    }

    @GetMapping
    @Operation(summary = "Every global criterion, by food; with compositionFoodId, that food's")
    public List<FoodMeasureDto> list(@RequestParam(required = false) Long compositionFoodId) {
        return criterionService.globalCriteria(compositionFoodId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One global criterion")
    public FoodMeasureDto get(@PathVariable Long id) {
        return criterionService.globalCriterion(id);
    }

    @GetMapping("/{id}/usage")
    @Operation(summary = "How many recipe ingredients and logged extras the criterion weighs now: "
            + "what a change to it reaches, live, in every diet")
    public MeasureUsageDto usage(@PathVariable Long id) {
        return criterionService.usage(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "A new global criterion: {measure, size?, compositionFoodId, grams | ml, note?}. "
            + "One per food, measure and size")
    public FoodMeasureDto create(@Valid @RequestBody MeasureCriterionRequestDto request) {
        return criterionService.createGlobal(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rewrites a global criterion, live for everything it weighs. While in use "
            + "only its weight and note may change")
    public FoodMeasureDto update(@PathVariable Long id,
                                 @Valid @RequestBody MeasureCriterionRequestDto request) {
        return criterionService.updateGlobal(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deletes a global criterion nothing is weighed by. One still in use is a 400")
    public void delete(@PathVariable Long id) {
        criterionService.deleteGlobal(id);
    }
}
