package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One plate: what it is called, what is on it, and — when it is known — the
 * sentence it was written as.
 *
 * <p>{@code rawText} is the cell a nutritionist typed, kept because reading it
 * cannot be undone. {@code "Tostada (60 gr) con tomate (80 gr)"} is stored as a
 * name and two quantities, and putting those back together gives a different
 * sentence with the same meaning. An editor that has to return the whole week
 * would rewrite every untouched cell that way, so the sentence itself travels.
 *
 * <p>It is null whenever nothing wrote one, and is <em>never</em> filled in
 * from the parts: a reconstruction that claims to be the original is worse than
 * an honest absence.
 */
public record Dish(
        @NotBlank String name,
        @Size(max = 1000) String rawText,
        @NotNull List<@NotNull @Valid DishIngredient> ingredients) {

    /** A dish whose written form is not known — assembled rather than typed. */
    public Dish(String name, List<DishIngredient> ingredients) {
        this(name, null, ingredients);
    }

    /** The same dish, read again into new ingredients. */
    public Dish withIngredients(List<DishIngredient> read) {
        return new Dish(name, rawText, read);
    }
}
