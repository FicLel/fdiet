package com.fdiet.diet.controller;

import com.fdiet.common.dto.PageDto;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietImportSummaryDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.ResolveIngredientDto;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.service.IDietImportService;
import com.fdiet.diet.service.IDietService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;

/**
 * Web layer. It forwards to the services and returns what they hand back.
 */
@RestController
@RequestMapping("/api/diets")
@Validated
@Tag(name = "Diet controller", description = "The diet in force, its history, and the import")
public class DietController {

    private final IDietService dietService;
    private final IDietImportService dietImportService;

    public DietController(IDietService dietService, IDietImportService dietImportService) {
        this.dietService = dietService;
        this.dietImportService = dietImportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Store a week as the diet in force, archiving the one it replaces. "
            + "An ingredient the food catalogue does not carry is stored unmatched, never dropped")
    public DietDto create(@RequestBody @Valid DietRequestDto diet) {
        return dietService.create(diet);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the whole week of a stored diet")
    public DietDto update(@PathVariable Long id, @RequestBody @Valid DietRequestDto diet) {
        return dietService.update(id, diet);
    }

    @GetMapping("/active")
    @Operation(summary = "The diet in force now, ordered by day and meal slot")
    public DietDto active() {
        return dietService.findActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One diet with its whole week")
    public DietDto byId(@PathVariable Long id) {
        return dietService.findById(id);
    }

    @GetMapping
    @Operation(summary = "The archived diets, most recently started first")
    public PageDto<DietSummaryDto> history(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return dietService.history(page, size);
    }

    @GetMapping("/{id}/ingredients")
    @Operation(summary = "A diet's ingredients. Pass resolved=false for the ones still waiting "
            + "to be matched to a food, and suggest=true to have the composition database's "
            + "best candidates ranked against each of them")
    public PageDto<DishIngredient> ingredients(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(defaultValue = "false") boolean suggest,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return dietService.ingredients(id, resolved, suggest, page, size);
    }

    @PatchMapping("/{id}/ingredients/{ingredientId}")
    @Operation(summary = "Match one ingredient to a food — bedcaFoodId for a composition-database "
            + "food, foodItemId for a branded product — or correct its name, quantity or unit. "
            + "Fields left out are left alone")
    public DishIngredient resolveIngredient(
            @PathVariable Long id,
            @PathVariable Long ingredientId,
            @RequestBody @Valid ResolveIngredientDto change) {
        return dietService.resolveIngredient(id, ingredientId, change);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import a diet from one sheet of an .xlsx laid out like example-ui.xlsx")
    public DietImportSummaryDto importWorkbook(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String sheet,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startedOn) {
        if (file.isEmpty()) {
            throw new InvalidDietException("The uploaded workbook is empty");
        }
        try (InputStream workbook = file.getInputStream()) {
            return dietImportService.importWorkbook(workbook, sheet, name, startedOn);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded workbook", e);
        }
    }
}
