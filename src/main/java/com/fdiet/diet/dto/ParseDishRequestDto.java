package com.fdiet.diet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Recipe text as the nutritionist typed it, to be read the way the workbook
 * import reads a cell — without storing anything.
 *
 * @param text     the ingredients as written, {@code Ensalada: lechuga
 *                 (80 gr) + tomate (100 gr)}
 * @param slotName what the row is called, {@code Primer plato}. The parser
 *                 falls back to it when the cell carries no {@code name:} of
 *                 its own, so a dish is never left nameless.
 * @param dietId   the diet the recipe is written in, when there is one: its own
 *                 household measures and its profile decide which measure
 *                 weighs "1 cdta", exactly as they will when the week is
 *                 published
 * @param keep     the matches the editor already holds for this text (FD-048).
 *                 An ingredient read under one of these names keeps that food
 *                 instead of what the name would match; a name that was edited
 *                 matches none, and parse decides. Null or empty keeps nothing.
 */
public record ParseDishRequestDto(
        @NotBlank String text,
        String slotName,
        Long dietId,
        List<@Valid KeptMatchDto> keep) {

    public ParseDishRequestDto(String text, String slotName) {
        this(text, slotName, null, null);
    }

    /** The matches to keep; never null. */
    public List<KeptMatchDto> keep() {
        return keep == null ? List.of() : keep;
    }
}
