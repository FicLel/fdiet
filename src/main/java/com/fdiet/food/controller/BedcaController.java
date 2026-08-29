package com.fdiet.food.controller;

import com.fdiet.common.dto.PageDto;
import com.fdiet.food.dto.BedcaFoodDto;
import com.fdiet.food.dto.BedcaSyncSummaryDto;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.IBedcaImportService;
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
@RequestMapping("/api/bedca")
@Validated
@Tag(name = "Composition database controller",
        description = "The generic foods a diet is written in, from AESAN/BEDCA")
public class BedcaController {

    private final IBedcaFoodService bedcaFoodService;
    private final IBedcaImportService bedcaImportService;

    public BedcaController(IBedcaFoodService bedcaFoodService,
                           IBedcaImportService bedcaImportService) {
        this.bedcaFoodService = bedcaFoodService;
        this.bedcaImportService = bedcaImportService;
    }

    @GetMapping
    @Operation(summary = "List composition-database foods, optionally filtered by name. "
            + "Each carries its figures as published, with their units, and the same "
            + "figures converted to kcal/g/mg")
    public PageDto<BedcaFoodDto> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return bedcaFoodService.search(name, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One composition-database food by its id")
    public BedcaFoodDto byId(@PathVariable Long id) {
        return bedcaFoodService.findById(id);
    }

    @PostMapping("/sync")
    @Operation(summary = "Import bedca_foods.csv into the database. Safe to re-run: a food "
            + "already stored is written over, ids are the source's own. The response "
            + "carries the attribution these figures may not be shown without")
    public BedcaSyncSummaryDto sync() {
        return bedcaImportService.sync();
    }
}
