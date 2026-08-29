package com.fdiet.diet.model;

import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.FoodItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * <p>The quantity keeps its own unit (g, ml, unidad) rather than being forced
 * into grams.
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

    /** What the diet called this food. Never null, matched or not. */
    @Column(name = "raw_name", length = 255, nullable = false)
    private String rawName;

    @Column(name = "quantity", precision = 10, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", length = 32, nullable = false)
    private String unit;

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

    /** True once the ingredient points at a food, whichever half it came from. */
    public boolean isMatched() {
        return foodItem != null || bedcaFood != null;
    }
}
