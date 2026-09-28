package com.fdiet.diet.model;

import com.fdiet.food.model.BedcaFood;
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
 * What a {@link PlannedDish} is made of, in the quantity the diet prescribes.
 *
 * <p>{@code rawName} is what the diet calls the food and is always there. The
 * nutrition figures come from whichever half of the catalogue it was matched
 * to: {@link BedcaFood}, the generic foods a diet is normally written in, or
 * {@link FoodItem} when the diet names a branded product. Both are null until
 * a match has been made — an ingredient nobody could match is kept as written
 * and matched later rather than dropped, which would silently lose part of the
 * week.
 *
 * <p>The quantity keeps its own unit (g, ml, unidad, cdta) rather than being
 * forced into grams. A unit that is a household measure is weighed through
 * {@link #foodMeasure} when one is attached, and not at all when none is.
 */
@Entity
@Table(name = "diet_ingredients")
@Getter
@Setter
public class PlannedIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dish_id", nullable = false)
    private PlannedDish dish;

    /** The branded catalogue product, when the diet names one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id")
    private FoodItem foodItem;

    /**
     * The generic food of the composition database — the usual match, since a
     * diet says "lechuga" and that is what {@code bedca_foods} is full of. Null
     * alongside {@link #foodItem} means the ingredient is still unmatched.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bedca_food_id")
    private BedcaFood bedcaFood;

    /**
     * The household measure that weighs a quantity written in one ("1 cdta",
     * "1 kiwi"), and who says what it weighs. Attached like a food match:
     * automatically only when the choice is not a judgement.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_measure_id")
    private ReferenceFoodMeasure foodMeasure;

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

    /** Its place in the dish, from 0. Unique within the dish. */
    @Column(name = "position", nullable = false)
    private int position;

    protected PlannedIngredient() {
    }

    public PlannedIngredient(String rawName,
                             FoodItem foodItem,
                             BedcaFood bedcaFood,
                             BigDecimal quantity,
                             String unit) {
        this.rawName = rawName;
        this.foodItem = foodItem;
        this.bedcaFood = bedcaFood;
        this.quantity = quantity;
        this.unit = unit;
    }

    /** Whether the quantity is still a range nobody has settled. */
    public boolean isRange() {
        return quantityMax != null;
    }

    /** True once the ingredient points at a food, whichever half it came from. */
    public boolean isMatched() {
        return foodItem != null || bedcaFood != null;
    }
}
