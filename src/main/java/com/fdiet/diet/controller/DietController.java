package com.fdiet.diet.controller;

import com.fdiet.common.dto.PageDto;
import com.fdiet.diet.dto.ComposeRequestDto;
import com.fdiet.diet.dto.ComposedFragmentDto;
import com.fdiet.diet.dto.CopyDietRequestDto;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietImportSummaryDto;
import com.fdiet.diet.dto.DietMeasureSavedDto;
import com.fdiet.diet.dto.DietRationsDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.DietSettingsDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.ParseDishRequestDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.dto.ResolveIngredientDto;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.service.IDietImportService;
import com.fdiet.diet.service.IDietService;
import com.fdiet.reference.dto.DietMeasureRequestDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.List;

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
    @Operation(summary = "Store a week as the patient's diet in force, archiving the one it "
            + "replaces. An ingredient the food catalogue does not carry is stored unmatched, "
            + "never dropped")
    public DietDto create(@RequestBody @Valid DietRequestDto diet) {
        return dietService.create(diet);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the whole week of a stored diet. The body's patientId must be "
            + "the patient the diet already belongs to — a diet changes hands by being copied, "
            + "not by being edited")
    public DietDto update(@PathVariable Long id, @RequestBody @Valid DietRequestDto diet) {
        return dietService.update(id, diet);
    }

    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Write the same week again for another patient — its days, its dishes, "
            + "the sentences they were typed as and every food match already made. It becomes "
            + "that patient's diet in force; the original is left exactly as it was, and the "
            + "journal is not copied")
    public DietDto copy(@PathVariable Long id, @RequestBody @Valid CopyDietRequestDto request) {
        return dietService.copy(id, request);
    }

    @GetMapping("/active")
    @Operation(summary = "One patient's diet in force now, ordered by day and meal slot")
    public DietDto active(@RequestParam Long patientId) {
        return dietService.findActive(patientId);
    }

    @GetMapping("/current")
    @Operation(summary = "Every patient's diet in force, without their weeks — who is on a diet "
            + "right now, in one request")
    public List<DietSummaryDto> current() {
        return dietService.current();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One diet with its whole week. Any patient's, from anywhere: there is "
            + "no security layer and none is implied here")
    public DietDto byId(@PathVariable Long id) {
        return dietService.findById(id);
    }

    @GetMapping
    @Operation(summary = "One patient's archived diets, most recently started first")
    public PageDto<DietSummaryDto> history(
            @RequestParam Long patientId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return dietService.history(patientId, page, size);
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

    @PostMapping("/parse")
    @Operation(summary = "Read recipe text into its ingredients, matched against the catalogues, "
            + "without storing anything. The editor asks for this so the text a recipe is written "
            + "in is read by the same parser the workbook import uses. The name is the one the "
            + "text carried before a colon, or slotName")
    public RecipeDto parse(@RequestBody @Valid ParseDishRequestDto request) {
        return dietService.parse(request);
    }

    @PostMapping("/compose")
    @Operation(summary = "Write a food added by ration or household measure as the text a recipe "
            + "holds — grams (a value inside a ration's range) or foodMeasureId with a count — and "
            + "read that text back through the parser. Stores nothing: the editor appends the "
            + "fragment to the recipe")
    public ComposedFragmentDto compose(@RequestBody @Valid ComposeRequestDto request) {
        return dietService.compose(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a diet, change the reference profile it is written against, or "
            + "mark it clinical, without sending its week. Fields left out are left alone")
    public DietDto updateSettings(@PathVariable Long id,
                                  @RequestBody @Valid DietSettingsDto settings) {
        return dietService.updateSettings(id, settings);
    }

    @GetMapping("/{id}/rations")
    @Operation(summary = "The week counted in rations per group against its reference profile "
            + "(or ?profile=), checked against the profile's recommendations, with meal energy "
            + "shares and — on a clinical diet — carbohydrate rations. Ranges stay ranges; every "
            + "day says what could not be counted and why")
    public DietRationsDto rations(@PathVariable Long id,
                                  @RequestParam(required = false) String profile) {
        return dietService.rations(id, profile);
    }

    @GetMapping("/{id}/measures")
    @Operation(summary = "The diet's own weights for household measures — the nutritionist's "
            + "criterion, never presented as a published figure")
    public List<FoodMeasureDto> measures(@PathVariable Long id) {
        return dietService.measures(id);
    }

    @PutMapping("/{id}/measures")
    @Operation(summary = "Set the diet's own weight for one household measure of one food (and "
            + "size), replacing an earlier one, and attach it to the diet's ingredients it weighs")
    public DietMeasureSavedDto saveMeasure(@PathVariable Long id,
                                           @RequestBody @Valid DietMeasureRequestDto request) {
        return dietService.saveMeasure(id, request);
    }

    @DeleteMapping("/{id}/measures/{measureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove one of the diet's own measures. The ingredients it weighed are "
            + "left unmeasured rather than weighed by something else")
    public void deleteMeasure(@PathVariable Long id, @PathVariable Long measureId) {
        dietService.deleteMeasure(id, measureId);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import a diet from one sheet of an .xlsx laid out like example-ui.xlsx, "
            + "as the given patient's diet in force. referenceProfile is the ration profile it is "
            + "written against; without one, the profile suggested for the patient's age")
    public DietImportSummaryDto importWorkbook(
            @RequestParam MultipartFile file,
            @RequestParam Long patientId,
            @RequestParam(required = false) String sheet,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startedOn,
            @RequestParam(required = false) String referenceProfile,
            @RequestParam(required = false) Boolean clinical) {
        if (file.isEmpty()) {
            throw new InvalidDietException("The uploaded workbook is empty");
        }
        try (InputStream workbook = file.getInputStream()) {
            return dietImportService.importWorkbook(workbook, patientId, sheet, name, startedOn,
                    referenceProfile, clinical);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded workbook", e);
        }
    }
}
