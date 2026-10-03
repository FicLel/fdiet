package com.fdiet.food.dto;

import com.fdiet.food.model.Nutrient;

import java.util.Map;

/**
 * One food of CIQUAL or BLS, typed but not yet stored, with the crosswalk row
 * that names it in Spanish when there is one. The readers build these and the
 * service that owns {@code composition_foods} stores them.
 *
 * <p>{@code nutrients} holds only the components the source published a number
 * for: a qualified or missing value is absent, never zero.
 */
public record CompositionFoodRowDto(
        CompositionKey key,
        String nameOriginal,
        String nameEn,
        String foodGroupCode,
        Map<Nutrient, NutrientDto> nutrients,
        CompositionLinkDto link) {

    /** The same row, named by that crosswalk row (or by none, when it is null). */
    public CompositionFoodRowDto withLink(CompositionLinkDto crosswalk) {
        return new CompositionFoodRowDto(key, nameOriginal, nameEn, foodGroupCode, nutrients, crosswalk);
    }

    /** Whether the source published an energy figure for this food. */
    public boolean energyPublished() {
        return nutrients.containsKey(Nutrient.ENERGY);
    }
}
