package com.fdiet.reference.controller;

import com.fdiet.common.helper.RetiredFields;
import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.HouseholdMeasureDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.ReferenceProfileDetailDto;
import com.fdiet.reference.dto.ReferenceProfileDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto;
import com.fdiet.reference.dto.YieldFactorDto;
import com.fdiet.reference.service.IReferenceImportService;
import com.fdiet.reference.service.IReferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Null;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Web layer. It forwards to the services and returns what they hand back.
 */
@RestController
@RequestMapping("/api/reference")
@Validated
@Tag(name = "Reference controller",
        description = "Rations, household measures and recommendations, each with its source")
public class ReferenceController {

    private final IReferenceService referenceService;
    private final IReferenceImportService importService;

    public ReferenceController(IReferenceService referenceService,
                               IReferenceImportService importService) {
        this.referenceService = referenceService;
        this.importService = importService;
    }

    @GetMapping("/sources")
    @Operation(summary = "Every source a reference figure comes from, with its licence class and "
            + "the attribution its figures may not be shown without")
    public List<ReferenceSourceDto> sources() {
        return referenceService.sources();
    }

    @GetMapping("/profiles")
    @Operation(summary = "The profiles a diet can be written against. Pass ageMonths to have the "
            + "one suggested for that age marked — an offer, never applied on its own")
    public List<ReferenceProfileDto> profiles(@RequestParam(required = false) @Min(0) Integer ageMonths) {
        return referenceService.profiles(ageMonths);
    }

    @GetMapping("/profiles/{code}")
    @Operation(summary = "One profile with its rations, recommendations, meal energy shares and "
            + "the sources of all of them")
    public ReferenceProfileDetailDto profile(@PathVariable String code) {
        return referenceService.profile(code);
    }

    @GetMapping("/rations")
    @Operation(summary = "Rations. With compositionFoodId (a CIQUAL or BLS food), the ones "
            + "covering that food: the profile's own first, then published per-food rations of "
            + "other sources. bedcaFoodId is retired: a value there is a 400")
    public List<RationDto> rations(
            @RequestParam(required = false) String profile,
            @RequestParam(required = false) Long compositionFoodId,
            @RequestParam(required = false) @Null(message = RetiredFields.BEDCA_FOOD_ID) Long bedcaFoodId) {
        return referenceService.rationsForFood(profile, compositionFoodId);
    }

    @GetMapping("/measures")
    @Operation(summary = "The household measures that can weigh a CIQUAL or BLS food, narrowed "
            + "to one written unit when given: the diet's own criteria first (with dietId), then "
            + "the nutritionist's global criteria, then published rows. bedcaFoodId is retired: "
            + "a value there is a 400")
    public List<FoodMeasureDto> measures(
            @RequestParam Long compositionFoodId,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) Long dietId,
            @RequestParam(required = false) String profile,
            @RequestParam(required = false) @Null(message = RetiredFields.BEDCA_FOOD_ID) Long bedcaFoodId) {
        return referenceService.measuresForFood(compositionFoodId, unit, dietId, profile);
    }

    @GetMapping("/yields")
    @Operation(summary = "Published cooking yields that could say what a food weighs raw or "
            + "cooked, most specific first and those whose method the food's name states ahead "
            + "of the rest. Offers only: nothing converts a quantity by them. The food is a "
            + "CIQUAL or BLS compositionFoodId; bedcaFoodId is retired: a value there is a 400")
    public List<YieldFactorDto> yields(
            @RequestParam Long compositionFoodId,
            @RequestParam(required = false) @Null(message = RetiredFields.BEDCA_FOOD_ID) Long bedcaFoodId) {
        return referenceService.yieldFactorsForFood(compositionFoodId);
    }

    @GetMapping("/vocabulary")
    @Operation(summary = "The household-measure words the parser reads, and their spellings")
    public List<HouseholdMeasureDto> vocabulary() {
        return referenceService.vocabulary();
    }

    @GetMapping("/exchange-systems")
    @Operation(summary = "Exchange unit definitions. Clinical ones only with clinical=true")
    public List<ExchangeSystemDto> exchangeSystems(
            @RequestParam(defaultValue = "false") boolean clinical) {
        return referenceService.exchangeSystems(clinical);
    }

    @PostMapping("/sync")
    @Operation(summary = "Load reference-data/ into the database. Safe to re-run: rows are keyed "
            + "on their code. Also runs at startup")
    public ReferenceSyncSummaryDto sync() {
        return importService.sync();
    }
}
