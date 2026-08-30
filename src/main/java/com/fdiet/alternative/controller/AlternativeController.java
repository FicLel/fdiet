package com.fdiet.alternative.controller;

import com.fdiet.alternative.dto.FoodAlternativesDto;
import com.fdiet.alternative.service.IAlternativeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Web layer. It forwards to the service and returns what it hands back.
 */
@RestController
@RequestMapping("/api/alternatives")
@Validated
@Tag(name = "Food alternative controller",
        description = "What a diet could put on the plate instead of a given food, "
                + "drawn from its own food family")
public class AlternativeController {

    private final IAlternativeService alternativeService;

    public AlternativeController(IAlternativeService alternativeService) {
        this.alternativeService = alternativeService;
    }

    @GetMapping("/{foodId}")
    @Operation(summary = "Alternatives to one composition-database food, best first. Only foods "
            + "of the same family are offered — a grilled chicken is answered with meats and "
            + "fish and never with a vegetable, however close the figures — and the order "
            + "within the family is how near the composition is. Pass grams to be told how "
            + "much of each alternative carries the same energy")
    public FoodAlternativesDto byFoodId(
            @PathVariable Long foodId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) @DecimalMin("0.01") BigDecimal grams,
            @RequestParam(defaultValue = "false") boolean sameFood) {
        return alternativeService.forFoodId(foodId, limit, grams, sameFood);
    }

    @GetMapping
    @Operation(summary = "The same, for a food named the way a diet names it. The name must be "
            + "one the composition database carries, matched exactly but case- and "
            + "accent-insensitively; anything less is a 404 rather than a guess, and is "
            + "resolved by a person through the diet's ingredient fix-up list first")
    public FoodAlternativesDto byName(
            @RequestParam @NotBlank String name,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) @DecimalMin("0.01") BigDecimal grams,
            @RequestParam(defaultValue = "false") boolean sameFood) {
        return alternativeService.forName(name, limit, grams, sameFood);
    }
}
