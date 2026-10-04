package com.fdiet.diet.model;

import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * What a {@link Recipe} is made of, in the quantity it prescribes for one serving.
 *
 * <p>{@code rawName} is what the diet calls the food and is always there. The
 * nutrition figures come from whichever half of the catalogue it was matched
 * to: {@link CompositionFood} (CIQUAL 2025 / BLS 4.0), the generic foods a diet
 * is normally written in, or {@link FoodItem} when the diet names a branded
 * product. Both are null until
 * a match has been made — an ingredient nobody could match is kept as written
 * and matched later rather than dropped, which would silently lose part of the
 * week.
 *
 * <p>The quantity keeps its own unit (g, ml, unidad, cdta) rather than being
 * forced into grams. A unit that is a household measure is weighed through
 * {@link #foodMeasure} when one is attached, and not at all when none is.
 */
@Entity
@Table(name = "recipe_ingredients")
@Getter
@Setter
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    /** The branded catalogue product, when the diet names one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id")
    private FoodItem foodItem;

    /**
     * The generic composition food — the usual match, since a diet says
     * "lechuga" and that is what fdiet's Spanish crosswalk names. Null alongside
     * {@link #foodItem} means the ingredient is still unmatched.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "composition_food_id")
    private CompositionFood compositionFood;

    /**
     * The household measure that weighs a quantity written in one ("1 cdta",
     * "1 kiwi"), and who says what it weighs. Attached like a food match:
     * automatically only when the choice is not a judgement.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_measure_id")
    private ReferenceFoodMeasure foodMeasure;

    /**
     * Whether a person picked {@link #foodMeasure} (FD-054): kept through every
     * re-read and publish while it still weighs the food and unit, and never
     * replaced by a new criterion. False when the rule chose it, which a new or
     * changed criterion chooses again. Meaningful only beside a measure; see
     * {@link #measurePicked()}.
     */
    @Column(name = "measure_picked", nullable = false)
    private boolean measurePicked;

    /** What the diet called this food. Never null, matched or not. */
    @Column(name = "raw_name", length = 255, nullable = false)
    private String rawName;

    @Column(name = "quantity", precision = 10, scale = 2, nullable = false)
    private BigDecimal quantity;

    /**
     * The upper bound when the text gave a range ("40-60 gr"), with
     * {@link #quantity} the lower one; null for a single value. A ranged
     * ingredient is weighed by nothing until a person confirms one value.
     */
    @Column(name = "quantity_max", precision = 10, scale = 2)
    private BigDecimal quantityMax;

    @Column(name = "unit", length = 32, nullable = false)
    private String unit;

    /** The raw/cooked word the text carried, or null when it carried none. */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16)
    private FoodState state;

    /** "Pequeña", "mediana", "grande", when the text said so. */
    @Enumerated(EnumType.STRING)
    @Column(name = "portion_size", length = 8)
    private PortionSize size;

    /** Its place in the recipe, from 0. Unique within the recipe. */
    @Column(name = "position", nullable = false)
    private int position;

    protected RecipeIngredient() {
    }

    public RecipeIngredient(String rawName,
                             FoodItem foodItem,
                             CompositionFood compositionFood,
                             BigDecimal quantity,
                             String unit) {
        this.rawName = rawName;
        this.foodItem = foodItem;
        this.compositionFood = compositionFood;
        this.quantity = quantity;
        this.unit = unit;
    }

    /** Whether the quantity is still a range nobody has settled. */
    public boolean isRange() {
        return quantityMax != null;
    }

    /** True once the ingredient points at a food, whichever half it came from. */
    public boolean isMatched() {
        return foodItem != null || compositionFood != null;
    }

    /**
     * Whether the quantity was written in one side of cooking and the matched
     * composition food is published in the other ({@code 55 g en seco} against
     * {@code Lenteja, cocida}), read off the food's Spanish name. A branded product
     * states nothing, and an unknown on either side never raises the flag.
     */
    public boolean isStateMismatch() {
        return compositionFood != null
                && FoodState.disagree(state, FoodState.ofFoodName(compositionFood.getNameEs()));
    }

    /**
     * Whether the attached measure is a person's pick. The flag alone says nothing
     * once the measure is gone: the measure's foreign key sets NULL on delete and
     * leaves the flag behind.
     */
    public boolean measurePicked() {
        return measurePicked && foodMeasure != null;
    }

    /**
     * The id of the composition food the ingredient is matched to, or null. A
     * reference row or a measure criterion names a composition food and is
     * compared with this. Reading the id of a lazy reference loads nothing.
     */
    public Long compositionFoodId() {
        return compositionFood == null ? null : compositionFood.getId();
    }
}
