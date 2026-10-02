package com.fdiet.diet.controller;

import com.fdiet.common.dto.PageDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.dto.RecipeUsageDto;
import com.fdiet.diet.service.IDietService;
import com.fdiet.diet.service.IRecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
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

/**
 * Web layer for the recipe library. It forwards to the services and returns what
 * they hand back. Where a recipe is served is the diet's question, since the
 * plates are the diet's table, so that one endpoint goes to the diet service.
 */
@RestController
@RequestMapping("/api/recipes")
@Validated
@Tag(name = "Recipe controller", description = "The shared recipe library a plate is picked from")
public class RecipeController {

    private final IRecipeService recipeService;
    private final IDietService dietService;

    public RecipeController(IRecipeService recipeService, IDietService dietService) {
        this.recipeService = recipeService;
        this.dietService = dietService;
    }

    @GetMapping
    @Operation(summary = "The library, by name. Private recipes — the ones written inside one "
            + "plate — are never listed")
    public PageDto<RecipeDto> library(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return recipeService.library(name, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One recipe, one serving, with its figures")
    public RecipeDto byId(@PathVariable Long id) {
        return recipeService.byId(id);
    }

    @GetMapping("/{id}/usage")
    @Operation(summary = "How many plates serve the recipe and in whose diets. An edit to a "
            + "library recipe reaches every one of them at once")
    public RecipeUsageDto usage(@PathVariable Long id) {
        return dietService.recipeUsage(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a recipe to the library: {name, steps?, rawText?, ingredients}. "
            + "Ingredients sent are kept with their matches; when none are sent, rawText is read. "
            + "The name must not be one another library recipe holds")
    public RecipeDto create(@RequestBody @Valid RecipeDto request) {
        return recipeService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rewrite a library recipe. Every plate that serves it changes with it, "
            + "without a publish")
    public RecipeDto update(@PathVariable Long id, @RequestBody @Valid RecipeDto request) {
        return recipeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a library recipe no plate serves. One still served is a 400")
    public void delete(@PathVariable Long id) {
        recipeService.delete(id);
    }
}
