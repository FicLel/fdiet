package com.fdiet.food.controller;

import com.fdiet.common.dto.PageDto;
import com.fdiet.food.dto.CompositionFoodDto;
import com.fdiet.food.dto.CompositionSyncSummaryDto;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.ICompositionImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web layer. It forwards to the services and returns what they hand back.
 */
@RestController
@RequestMapping("/api/composition")
@Validated
@Tag(name = "Open composition controller",
        description = "CIQUAL 2025 and BLS 4.0 foods (CC BY 4.0), with fdiet's Spanish names")
public class CompositionController {

    private final ICompositionFoodService compositionFoodService;
    private final ICompositionImportService compositionImportService;

    public CompositionController(ICompositionFoodService compositionFoodService,
                                 ICompositionImportService compositionImportService) {
        this.compositionFoodService = compositionFoodService;
        this.compositionImportService = compositionImportService;
    }

    @GetMapping
    @Operation(summary = "List CIQUAL and BLS foods. Without a name, by source and English name; "
            + "with one, foods whose Spanish name or aliases share its words first (most words "
            + "shared first), then any food whose Spanish, English or original name contains the "
            + "text as typed. Each carries its source and attribution, its figures as published "
            + "with their units, and the same figures converted to kcal/g/mg")
    public PageDto<CompositionFoodDto> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return compositionFoodService.search(name, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One CIQUAL or BLS food by its id")
    public CompositionFoodDto byId(@PathVariable Long id) {
        return compositionFoodService.findById(id);
    }

    @PostMapping("/sync")
    @Operation(summary = "Load the CIQUAL 2025 and BLS 4.0 snapshots and fdiet's Spanish-name "
            + "crosswalk from reference-data/composition/. Safe to re-run: a food is found again "
            + "by its source and code and written over, its id kept. The response carries the "
            + "CIQUAL and BLS attribution these figures may not be shown without")
    public CompositionSyncSummaryDto sync() {
        return compositionImportService.sync();
    }
}
