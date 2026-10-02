package com.fdiet.diet.helpers;

import com.fdiet.diet.dto.RecipeDto;

/**
 * Turns one cell of a diet — a sentence a nutritionist wrote — into a dish and
 * its ingredients: a recipe, named by the text or by the row it sits in.
 */
public interface IMealTextParser {

    /**
     * The recipe the text describes, or {@code null} when the text is blank.
     *
     * @param text         the cell, e.g. {@code "Ensalada: lechuga (80 gr) + tomate (100 gr)"}
     * @param fallbackName the dish name to use when the text does not carry one
     *                     of its own — the row label of the workbook
     */
    RecipeDto parse(String text, String fallbackName);
}
