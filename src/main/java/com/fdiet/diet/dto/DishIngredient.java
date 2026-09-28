package com.fdiet.diet.dto;

import com.fdiet.food.dto.FoodSuggestionDto;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.dto.FoodMeasureDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * One food of a {@link Dish}, in the quantity the diet prescribes.
 *
 * <p>{@code name} is what the diet calls the food — the words the nutritionist
 * wrote, or the cell of the imported workbook. It is matched against either
 * half of the catalogue: {@code bedcaFoodId} for the generic composition
 * database, which is how a diet is normally written, or {@code foodItemId} when
 * it names a branded product. Both null means the match has not been made yet;
 * an unmatched ingredient is kept exactly as written and matched later, never
 * dropped.
 *
 * <p>{@code state} and {@code size} are the words the text carried ("crudo",
 * "pequeña"), null when it carried none. {@code foodMeasureId} is the household
 * measure that weighs a quantity written in one; sent back, it keeps a measure a
 * person picked.
 *
 * <p>The rest is filled on the way out and ignored on the way in: {@code id},
 * {@code matchedName} (what the matched food is called in the catalogue),
 * {@code measure} (the attached measure, with its source), {@code stateMismatch}
 * (the text says raw and the matched food is cooked, or the reverse — shown,
 * never converted), {@code nutrition} (this ingredient's own quantity, not per
 * 100 g) and {@code suggestions}, which the fix-up listing fills when asked to.
 */
public record DishIngredient(
        Long id,
        @NotBlank String name,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String unit,
        FoodState state,
        PortionSize size,
        Long foodItemId,
        Long bedcaFoodId,
        Long foodMeasureId,
        String matchedName,
        FoodMeasureDto measure,
        Boolean stateMismatch,
        NutritionDto nutrition,
        List<FoodSuggestionDto> suggestions) {

    /**
     * Boxed so a request body may leave it out — it is filled on the way out and
     * a primitive would refuse the missing field — and never null once built.
     */
    public DishIngredient {
        stateMismatch = Boolean.TRUE.equals(stateMismatch);
    }

    /** A freshly written ingredient: no id yet, and not matched to anything. */
    public DishIngredient(String name, BigDecimal quantity, String unit) {
        this(name, quantity, unit, null, null);
    }

    /** A freshly written ingredient with the state and size its text carried. */
    public DishIngredient(String name, BigDecimal quantity, String unit, FoodState state,
                          PortionSize size) {
        this(null, name, quantity, unit, state, size, null, null, null, null, null, false, null,
                null);
    }

    /** The same ingredient with a list of candidates attached. */
    public DishIngredient withSuggestions(List<FoodSuggestionDto> candidates) {
        return new DishIngredient(id, name, quantity, unit, state, size, foodItemId, bedcaFoodId,
                foodMeasureId, matchedName, measure, stateMismatch, nutrition, candidates);
    }

    public boolean resolved() {
        return foodItemId != null || bedcaFoodId != null;
    }
}
