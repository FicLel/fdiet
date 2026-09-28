package com.fdiet.diet.dto;

/**
 * The text a composed food is written as, and what the parser reads back out of
 * it.
 *
 * <p>The editor appends {@code fragment} to the cell and sends the cell to be
 * parsed like any other: there is one parser, and the composer only writes text
 * for it. {@code ingredient} is that fragment already read — matched, weighed
 * and priced — so what is about to be added can be shown before it is.
 */
public record ComposedFragmentDto(
        String fragment,
        DishIngredient ingredient) {
}
