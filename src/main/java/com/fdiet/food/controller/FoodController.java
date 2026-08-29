package com.fdiet.food.controller;

import com.fdiet.common.dto.PageDto;
import com.fdiet.food.dto.FoodItemDto;
import com.fdiet.food.dto.ImportSummaryDto;
import com.fdiet.food.service.FoodImportService;
import com.fdiet.food.service.IFoodItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Web layer. It only forwards to the services and returns what they hand back.
 */
@RestController
@RequestMapping("/api/food")
@Validated
@Tag(name = "Food controller", description = "Food actions and controller")
public class FoodController {

    private final IFoodItemService foodItemService;
    private final FoodImportService foodImportService;

    public FoodController(IFoodItemService foodItemService, FoodImportService foodImportService) {
        this.foodItemService = foodItemService;
        this.foodImportService = foodImportService;
    }

    @GetMapping
    @Operation(summary = "List food items, optionally filtered by commercial name")
    public PageDto<FoodItemDto> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return foodItemService.search(name, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find a single food item by its id")
    public FoodItemDto byId(@PathVariable Long id) {
        return foodItemService.findById(id);
    }

    @PostMapping("/sync")
    @Operation(summary = "Import fooddata.csv into the database")
    public ImportSummaryDto sync() {
        return foodImportService.importFromCsv();
    }
}
