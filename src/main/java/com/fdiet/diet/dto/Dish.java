package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record Dish(
        @NotBlank String name,
        @NotNull List<@NotNull @Valid DishIngredient> ingredients) {
}
