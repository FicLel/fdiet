package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * What goes into a plate, for one serving, and how it is made.
 *
 * <p>{@code rawText} is the ingredients as the nutritionist wrote them
 * ({@code "2 huevos + 5 ml AOVE + sal"}), kept because reading them into
 * {@code ingredients} cannot be undone; it is null where nothing wrote one and is
 * never rebuilt from the parts. {@code steps} is the preparation, free text.
 *
 * <p>{@code library} says whether the recipe is shared: a library recipe is read
 * by every plate that points at it, and changing it changes all of them.
 *
 * <p>Filled on the way out and ignored on the way in: {@code id},
 * {@code library} and {@code nutrition} — one serving's figures, with the counts
 * they rest on. A plate's own figures, at its servings, are on the {@link Dish}.
 *
 * <p>{@code name} may be left out of a recipe written inside a plate; the plate's
 * description is used. A library recipe must carry one: it is how it is found
 * again.
 */
public record RecipeDto(
        Long id,
        @Size(max = 255) String name,
        Boolean library,
        @Size(max = 4000) String steps,
        @Size(max = 1000) String rawText,
        @NotNull List<@NotNull @Valid DishIngredient> ingredients,
        NutritionSummaryDto nutrition) {

    public RecipeDto {
        library = Boolean.TRUE.equals(library);
    }

    /** A recipe freshly read from text: nothing stored, no steps yet. */
    public RecipeDto(String name, String rawText, List<DishIngredient> ingredients) {
        this(null, name, false, null, rawText, ingredients, null);
    }

    /** The same recipe, read again into new ingredients. */
    public RecipeDto withIngredients(List<DishIngredient> read) {
        return new RecipeDto(id, name, library, steps, rawText, read, nutrition);
    }
}
