package com.fdiet.diet.dto;

/**
 * A diet as a measure is chosen inside it: its own criteria are found by its id,
 * and its reference profile settles a disagreement between published rows.
 *
 * @param key         what the caller asked about — the diet itself, or a private
 *                    recipe one of its plates serves
 * @param profileCode the diet's reference profile, or null for none
 */
public record DietProfileDto(
        Long key,
        Long dietId,
        String profileCode) {
}
