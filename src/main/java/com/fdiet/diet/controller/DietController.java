package com.fdiet.diet.controller;

import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.service.DietService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/diet")
@Validated
@Tag(name = "Diet Controller", description = "Builder and import of weekly diet")
public class DietController {

    private final DietService dietService;

    public DietController(DietService dietService) {
        this.dietService = dietService;
    }

    @PostMapping
    @Operation(summary = "Validate a weekly diet, returned ordered by day and meal slot. "
            + "An empty diet is accepted: the user may fill it in later")
    public List<DietDay> create(@RequestBody @Valid List<@NotNull @Valid DietDay> diet) {
        return dietService.create(diet);
    }
}
