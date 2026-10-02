package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One plate: what the patient reads it as, and the recipe behind it.
 *
 * <p>{@code name} is the description — "Huevos revueltos" — and nothing reads
 * food out of it. The food is the {@code recipe}, served {@code servings} times
 * (1 when left out): a shared recipe is written for one serving, and a patient
 * who needs more is given more servings of it rather than a copy.
 *
 * <p>On the way in a plate names its recipe one of two ways, or not at all:
 * <ul>
 *   <li>{@code recipeId} — a library recipe, or a private recipe this diet
 *       already holds and is keeping as it was;</li>
 *   <li>{@code recipe} — a private recipe written in the plate, read and matched
 *       the way an imported cell is.</li>
 * </ul>
 * Both at once is refused: one of them would have to be ignored. Neither is a
 * plate that is a description only.
 *
 * <p>On the way out {@code recipe} is filled, {@code recipeId} is its id, and
 * {@code nutrition} is the plate's figures at its servings.
 */
public record Dish(
        @NotBlank @Size(max = 255) String name,
        @Positive @DecimalMax("99") BigDecimal servings,
        Long recipeId,
        @Valid RecipeDto recipe,
        NutritionSummaryDto nutrition) {

    public Dish {
        servings = servings == null ? BigDecimal.ONE : servings;
    }

    /** A plate written with its own recipe, one serving. */
    public Dish(String name, RecipeDto recipe) {
        this(name, BigDecimal.ONE, null, recipe, null);
    }
}
