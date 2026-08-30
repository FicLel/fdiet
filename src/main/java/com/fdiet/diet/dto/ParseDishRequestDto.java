package com.fdiet.diet.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * One cell of a diet as the nutritionist typed it, to be read the way the
 * workbook import reads it — without storing anything.
 *
 * @param text     the line written in the cell, {@code Ensalada: lechuga
 *                 (80 gr) + tomate (100 gr)}
 * @param slotName what the row is called, {@code Primer plato}. The parser
 *                 falls back to it when the cell carries no {@code name:} of
 *                 its own, so a dish is never left nameless.
 */
public record ParseDishRequestDto(
        @NotBlank String text,
        String slotName) {
}
