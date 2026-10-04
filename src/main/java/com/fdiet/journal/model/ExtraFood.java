package com.fdiet.journal.model;

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
import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * Something eaten on a day of the week that the plan did not prescribe.
 *
 * <p>Shaped like {@link com.fdiet.diet.model.RecipeIngredient} on purpose,
 * because it is the same idea seen from the other side: a name as it was
 * written, a quantity with its own unit, and at most one of the two catalogues
 * pointed at. {@code rawName} is always kept — an entry nothing matched is
 * still an entry, and dropping it would quietly shorten the record of what was
 * actually eaten.
 *
 * <p>The branded catalogue is the usual match here, the reverse of the week: a
 * patient logging an extra is normally holding a wrapper with an EAN on it,
 * while a diet says "lechuga".
 */
@Entity
@Table(name = "extra_foods")
@Getter
@Setter
public class ExtraFood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diet_id", nullable = false)
    private Long dietId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 16, nullable = false)
    private DayOfWeek dayOfWeek;

    /** What the patient logged. Never null, matched or not. */
    @Column(name = "raw_name", length = 255, nullable = false)
    private String rawName;

    @Column(name = "quantity", precision = 10, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", length = 32, nullable = false)
    private String unit;

    /** The raw/cooked word, when the entry carried one. */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16)
    private FoodState state;

    /** "Pequeña", "mediana", "grande", which decides which measure row can weigh one piece. */
    @Enumerated(EnumType.STRING)
    @Column(name = "portion_size", length = 8)
    private PortionSize size;

    /**
     * The household measure that weighs a quantity logged in one ("1 cucharada",
     * "1 pieza"), chosen by the same rule as an ingredient of the week: attached
     * on its own only when the choice is not a judgement.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_measure_id")
    private ReferenceFoodMeasure foodMeasure;

    /**
     * The generic composition food (CIQUAL 2025 / BLS 4.0), when that is the
     * match. {@code bedca_food_id} stays in the table, null, until FD-033 phase E.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "composition_food_id")
    private CompositionFood compositionFood;

    /** The branded product, the usual match for an extra. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id")
    private FoodItem foodItem;

    @Column(name = "logged_at", nullable = false)
    private LocalDateTime loggedAt;

    protected ExtraFood() {
    }

    public ExtraFood(Long dietId,
                     DayOfWeek dayOfWeek,
                     String rawName,
                     BigDecimal quantity,
                     String unit,
                     CompositionFood compositionFood,
                     FoodItem foodItem) {
        this.dietId = dietId;
        this.dayOfWeek = dayOfWeek;
        this.rawName = rawName;
        this.quantity = quantity;
        this.unit = unit;
        this.compositionFood = compositionFood;
        this.foodItem = foodItem;
        this.loggedAt = LocalDateTime.now();
    }

    /** True once the entry points at a food, whichever half it came from. */
    public boolean isMatched() {
        return compositionFood != null || foodItem != null;
    }
}
