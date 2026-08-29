package com.fdiet.diet.helpers;

import com.fdiet.diet.dto.Dish;

/**
 * Turns one cell of a diet — a sentence a nutritionist wrote — into a dish and
 * its ingredients.
 */
public interface IMealTextParser {

    /**
     * The dish the text describes, or {@code null} when the text is blank.
     *
     * @param text         the cell, e.g. {@code "Ensalada: lechuga (80 gr) + tomate (100 gr)"}
     * @param fallbackName the dish name to use when the text does not carry one
     *                     of its own — the row label of the workbook
     */
    Dish parse(String text, String fallbackName);
}
