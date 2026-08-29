package com.fdiet.diet.dto;

import com.fdiet.food.dto.FoodSuggestionDto;
import com.fdiet.food.dto.NutritionDto;
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
 * <p>The last four fields are filled on the way out and ignored on the way in:
 * {@code id}, {@code matchedName} (what the matched food is called in the
 * catalogue), {@code nutrition} (this ingredient's own quantity, not per 100 g)
 * and {@code suggestions}, which the fix-up listing fills when it is asked to.
 */
public record DishIngredient(
        Long id,
        @NotBlank String name,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String unit,
        Long foodItemId,
        Long bedcaFoodId,
        String matchedName,
        NutritionDto nutrition,
        List<FoodSuggestionDto> suggestions) {

    /** A freshly written ingredient: no id yet, and not matched to anything. */
    public DishIngredient(String name, BigDecimal quantity, String unit) {
        this(null, name, quantity, unit, null, null, null, null, null);
    }

    /** The same ingredient with a list of candidates attached. */
    public DishIngredient withSuggestions(List<FoodSuggestionDto> candidates) {
        return new DishIngredient(id, name, quantity, unit, foodItemId, bedcaFoodId,
                matchedName, nutrition, candidates);
    }

    public boolean resolved() {
        return foodItemId != null || bedcaFoodId != null;
    }
}
