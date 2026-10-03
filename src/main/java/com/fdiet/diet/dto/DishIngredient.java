package com.fdiet.diet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fdiet.food.dto.FoodSuggestionDto;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.UnitWording;
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
 * <p>{@code quantityMax} is the upper bound of a quantity written as a range
 * ({@code 2-3 nueces}, {@code (40-60 gr)}), with {@code quantity} the lower one,
 * and null for a single value. A range is kept as written and counted nowhere
 * until a person confirms one value.
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
 * never converted), {@code yieldHint} (on such a mismatch, what the quantity
 * would weigh in the food's state by a published cooking yield — offered, never
 * applied), {@code nutrition} (this ingredient's own quantity, not per
 * 100 g) and {@code suggestions}, which the fix-up listing fills when asked to.
 *
 * <p>{@code unitWording} is the unit said in both numbers with its size agreeing
 * ({@code unidad mediana} / {@code unidades medianas}), derived from {@code name},
 * {@code unit} and {@code size} on the way out and ignored on the way in; see
 * {@link UnitWording}.
 */
public record DishIngredient(
        Long id,
        @NotBlank String name,
        @NotNull @Positive BigDecimal quantity,
        @Positive BigDecimal quantityMax,
        @NotBlank String unit,
        FoodState state,
        PortionSize size,
        Long foodItemId,
        Long bedcaFoodId,
        Long foodMeasureId,
        String matchedName,
        FoodMeasureDto measure,
        Boolean stateMismatch,
        YieldHintDto yieldHint,
        NutritionDto nutrition,
        List<FoodSuggestionDto> suggestions) {

    /**
     * Boxed so a request body may leave it out — it is filled on the way out and
     * a primitive would refuse the missing field — and never null once built.
     */
    public DishIngredient {
        stateMismatch = Boolean.TRUE.equals(stateMismatch);
        // A bound no higher than the value is no range at all.
        if (quantityMax != null && quantity != null && quantityMax.compareTo(quantity) <= 0) {
            quantityMax = null;
        }
    }

    /** A freshly written ingredient: no id yet, and not matched to anything. */
    public DishIngredient(String name, BigDecimal quantity, String unit) {
        this(name, quantity, unit, null, null);
    }

    /** A freshly written ingredient with the state and size its text carried. */
    public DishIngredient(String name, BigDecimal quantity, String unit, FoodState state,
                          PortionSize size) {
        this(name, quantity, null, unit, state, size);
    }

    /** A freshly written ingredient whose quantity may be a range. */
    public DishIngredient(String name, BigDecimal quantity, BigDecimal quantityMax, String unit,
                          FoodState state, PortionSize size) {
        this(null, name, quantity, quantityMax, unit, state, size, null, null, null, null, null,
                false, null, null, null);
    }

    /** The same ingredient with a list of candidates attached. */
    public DishIngredient withSuggestions(List<FoodSuggestionDto> candidates) {
        return new DishIngredient(id, name, quantity, quantityMax, unit, state, size, foodItemId,
                bedcaFoodId, foodMeasureId, matchedName, measure, stateMismatch, yieldHint, nutrition,
                candidates);
    }

    /** The unit as the patient reads it, in both numbers; derived, never stored. */
    @JsonProperty(value = "unitWording", access = JsonProperty.Access.READ_ONLY)
    public UnitWording unitWording() {
        return UnitWording.of(name, unit, size);
    }

    /** Whether the quantity is a range nobody has settled yet. */
    public boolean range() {
        return quantityMax != null;
    }

    public boolean resolved() {
        return foodItemId != null || bedcaFoodId != null;
    }
}
