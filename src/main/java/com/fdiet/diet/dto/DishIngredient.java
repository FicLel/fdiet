package com.fdiet.diet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record DishIngredient(
        @NotBlank String name,
        @Positive double quantity,
        @NotBlank String unity) {
}
