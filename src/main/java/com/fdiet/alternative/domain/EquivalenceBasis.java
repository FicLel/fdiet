package com.fdiet.alternative.domain;

import com.fdiet.food.dto.NutritionDto;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * What an equivalent portion holds constant: the same energy, or the same grams
 * of one macronutrient. Energy is the default because every BEDCA row publishes
 * it (145 CIQUAL foods do not, and get no equivalent weight by it — {@link #of}
 * is null for them); the others are how a nutritionist who plans
 * in exchanges asks the question ("how much rice carries the carbohydrate of this
 * bread").
 *
 * <p>It is the <em>criterion</em> of an equivalence and never its eligibility:
 * the category still decides who may stand in for a food, whatever the basis.
 *
 * <p>{@code floor} is the least a food must carry per 100 g for an equivalent
 * weight of it to mean anything. A lettuce carries a gram of protein, and the
 * weight of lettuce with the protein of a chicken breast is a number no plate
 * holds; below the floor the answer is "no equivalent" rather than a kilo.
 */
public enum EquivalenceBasis {
    ENERGY(NutritionDto::energyKcal, new BigDecimal("0.01"), "energía", "kcal"),
    CARBOHYDRATE(NutritionDto::carbohydratesG, new BigDecimal("2"), "hidratos de carbono", "g"),
    PROTEIN(NutritionDto::proteinG, new BigDecimal("2"), "proteína", "g"),
    FAT(NutritionDto::fatG, new BigDecimal("2"), "grasa", "g");

    private final Function<NutritionDto, BigDecimal> figure;
    private final BigDecimal floor;
    private final String label;
    private final String unit;

    EquivalenceBasis(Function<NutritionDto, BigDecimal> figure, BigDecimal floor, String label,
                     String unit) {
        this.figure = figure;
        this.floor = floor;
        this.label = label;
        this.unit = unit;
    }

    /** The figure held constant, or null when the food does not publish it. */
    public BigDecimal of(NutritionDto nutrition) {
        return nutrition == null ? null : figure.apply(nutrition);
    }

    /** Whether a food carries enough of the figure, per 100 g, to be weighed against it. */
    public boolean carries(NutritionDto per100g) {
        BigDecimal value = of(per100g);
        return value != null && value.compareTo(floor) >= 0;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }
}
