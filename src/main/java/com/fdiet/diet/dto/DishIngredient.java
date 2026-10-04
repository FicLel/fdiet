package com.fdiet.diet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fdiet.common.helper.RetiredFields;
import com.fdiet.food.dto.CompositionSuggestionDto;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.UnitWording;
import com.fdiet.reference.dto.FoodMeasureDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * One food of a {@link Dish}, in the quantity the diet prescribes.
 *
 * <p>{@code name} is what the diet calls the food — the words the nutritionist
 * wrote, or the cell of the imported workbook. It is matched against either
 * half of the catalogue: {@code compositionFoodId} for a CIQUAL 2025 / BLS 4.0
 * food, which is how a diet is normally written, or {@code foodItemId} when it
 * names a branded product. Both null means the match has not been made yet; an
 * unmatched ingredient is kept exactly as written and matched later, never
 * dropped. {@code bedcaFoodId} is read only to be refused (FD-033): a value there
 * is a 400, and it is never written out.
 *
 * <p>{@code quantityMax} is the upper bound of a quantity written as a range
 * ({@code 2-3 nueces}, {@code (40-60 gr)}), with {@code quantity} the lower one,
 * and null for a single value. A range is kept as written and counted nowhere
 * until a person confirms one value.
 *
 * <p>{@code state} and {@code size} are the words the text carried ("crudo",
 * "pequeña"), null when it carried none. {@code foodMeasureId} is the household
 * measure that weighs a quantity written in one. {@code measurePicked} says whether
 * a person picked it (FD-054): sent back with {@code true}, the measure is kept
 * while it still weighs the food and unit (FD-039); with {@code false} it was the
 * rule's choice and the rule chooses again; left out beside a measure, it counts
 * as picked, so nothing a person chose is lost.
 *
 * <p>The rest is filled on the way out and ignored on the way in: {@code id},
 * {@code matchedName} (what the matched food is called in the catalogue),
 * {@code matchedSource} (the table a matched composition food comes from, null
 * for a branded product),
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
        Long compositionFoodId,
        Long foodMeasureId,
        Boolean measurePicked,
        String matchedName,
        CompositionSource matchedSource,
        FoodMeasureDto measure,
        Boolean stateMismatch,
        YieldHintDto yieldHint,
        NutritionDto nutrition,
        List<CompositionSuggestionDto> suggestions,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Null(message = RetiredFields.BEDCA_FOOD_ID) Long bedcaFoodId) {

    /**
     * Boxed so a request body may leave them out — they are filled on the way out
     * and a primitive would refuse the missing field — and never null once built.
     * {@code measurePicked} is true only beside a measure, and a measure sent
     * without it counts as picked: a caller that predates FD-054 keeps what it
     * sends, as it always did.
     */
    public DishIngredient {
        stateMismatch = Boolean.TRUE.equals(stateMismatch);
        measurePicked = foodMeasureId != null && !Boolean.FALSE.equals(measurePicked);
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
        this(null, name, quantity, quantityMax, unit, state, size, null, null, null, false, null, null,
                null, false, null, null, null, null);
    }

    /** The same ingredient with a list of candidates attached. */
    public DishIngredient withSuggestions(List<CompositionSuggestionDto> candidates) {
        return new DishIngredient(id, name, quantity, quantityMax, unit, state, size, foodItemId,
                compositionFoodId, foodMeasureId, measurePicked, matchedName, matchedSource, measure,
                stateMismatch, yieldHint, nutrition, candidates, bedcaFoodId);
    }

    /**
     * The same ingredient pinned to a composition food and a household measure a
     * caller already chose — either may be null to leave that one as it is. A
     * measure pinned here is a person's pick (the composer's measure choice).
     */
    public DishIngredient pinnedTo(Long foodId, Long measureId) {
        return new DishIngredient(id, name, quantity, quantityMax, unit, state, size, foodItemId,
                foodId == null ? compositionFoodId : foodId,
                measureId == null ? foodMeasureId : measureId,
                measureId != null || measurePicked,
                matchedName, matchedSource, measure, stateMismatch, yieldHint, nutrition, suggestions,
                bedcaFoodId);
    }

    /**
     * The same ingredient matched to exactly the food a caller kept — a composition
     * food or a branded product, the other released — with the measure it picked,
     * when it picked one (FD-048). A kept measure is a pick (FD-054).
     */
    public DishIngredient matchedTo(Long compositionId, Long itemId, Long measureId) {
        return new DishIngredient(id, name, quantity, quantityMax, unit, state, size, itemId,
                compositionId, measureId == null ? foodMeasureId : measureId,
                measureId != null || measurePicked,
                matchedName, matchedSource, measure, stateMismatch, yieldHint, nutrition, suggestions,
                bedcaFoodId);
    }

    /**
     * The measure a person picked, which the rule keeps while it still fits; null
     * when the measure is the rule's own choice, so the rule chooses again.
     */
    public Long pickedMeasureId() {
        return measurePicked ? foodMeasureId : null;
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
        return foodItemId != null || compositionFoodId != null;
    }
}
