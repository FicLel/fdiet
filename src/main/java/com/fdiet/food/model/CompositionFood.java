package com.fdiet.food.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One food of an open composition table — CIQUAL 2025 or BLS 4.0 — with its
 * composition per 100 g of edible part, as that table publishes it.
 *
 * <p>Two kinds of column live here and they come from different files:
 * <ul>
 *   <li>the source's own: {@code source}, {@code source_code}, the names it
 *       publishes and the fourteen figures, each with its unit. A qualified
 *       value ({@code traces}, {@code < 0,2}, {@code <LOQ}, {@code -}) is a
 *       blank, never a number;</li>
 *   <li>fdiet's crosswalk ({@code reference-data/composition/composition-es/links.csv}):
 *       the Spanish name a diet is matched on, its aliases, which row answers a
 *       name two rows share, whether a person approved it, and the edible
 *       portion read from USDA SR Legacy. Null on every food the crosswalk does
 *       not name yet.</li>
 * </ul>
 *
 * <p>The id is fdiet's; {@code (source, source_code)} is the stable key a sync
 * writes over, so an id never changes once a food is stored.
 */
@Entity
@Table(name = "composition_foods")
@Getter
@Setter
public class CompositionFood implements CompositionFigures {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false)
    private CompositionSource source;

    /** CIQUAL's {@code alim_code} or BLS's {@code BLS Code}, as published. */
    @Column(name = "source_code", length = 32, nullable = false)
    private String sourceCode;

    /** The name in the source's own language: French for CIQUAL, German for BLS. */
    @Column(name = "name_original", length = 255, nullable = false)
    private String nameOriginal;

    @Column(name = "name_en", length = 255)
    private String nameEn;

    /** CIQUAL's sub-group code ({@code 0101}) or the first letter of a BLS code ({@code G}). */
    @Column(name = "food_group_code", length = 16)
    private String foodGroupCode;

    /** fdiet's Spanish name, head first ({@code Pollo, pechuga, plancha}); null until crosswalked. */
    @Column(name = "name_es", length = 255)
    private String nameEs;

    /** Other ways a diet writes the food, {@code ;}-separated as the crosswalk holds them. */
    @Column(name = "name_aliases", length = 1000)
    private String nameAliases;

    /** Whether this row answers a name another crosswalk row also claims. */
    @Column(name = "name_preferred", nullable = false)
    private boolean namePreferred;

    /** Whether a person approved the crosswalk row. Machine prefill is never approved. */
    @Column(name = "name_reviewed", nullable = false)
    private boolean nameReviewed;

    /**
     * The edible fraction of the food as purchased, {@code 1 − refuse} of the
     * USDA SR Legacy food in {@link #ediblePortionFdcId}. Null when no SR Legacy
     * food fits — and a null refuses a gross weight, as it does for BEDCA.
     */
    @Column(name = "edible_portion", precision = 8, scale = 6)
    private BigDecimal ediblePortion;

    @Column(name = "edible_portion_fdc_id")
    private Integer ediblePortionFdcId;

    /** Energy in kcal, Regulation (EU) 1169/2011 for CIQUAL, {@code ENERCC} for BLS. */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "energy"))
    @AttributeOverride(name = "unit", column = @Column(name = "energy_unit", length = 16))
    private NutrientValue energy;

    /** CIQUAL: nitrogen × Jones factor. BLS: nitrogen × 6.25. */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "protein"))
    @AttributeOverride(name = "unit", column = @Column(name = "protein_unit", length = 16))
    private NutrientValue protein;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "fat"))
    @AttributeOverride(name = "unit", column = @Column(name = "fat_unit", length = 16))
    private NutrientValue fat;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "saturated_fat"))
    @AttributeOverride(name = "unit", column = @Column(name = "saturated_fat_unit", length = 16))
    private NutrientValue saturatedFat;

    /** Available carbohydrate. */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "carbohydrates"))
    @AttributeOverride(name = "unit", column = @Column(name = "carbohydrates_unit", length = 16))
    private NutrientValue carbohydrates;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "sugars"))
    @AttributeOverride(name = "unit", column = @Column(name = "sugars_unit", length = 16))
    private NutrientValue sugars;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "fiber"))
    @AttributeOverride(name = "unit", column = @Column(name = "fiber_unit", length = 16))
    private NutrientValue fiber;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "water"))
    @AttributeOverride(name = "unit", column = @Column(name = "water_unit", length = 16))
    private NutrientValue water;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "sodium"))
    @AttributeOverride(name = "unit", column = @Column(name = "sodium_unit", length = 16))
    private NutrientValue sodium;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "potassium"))
    @AttributeOverride(name = "unit", column = @Column(name = "potassium_unit", length = 16))
    private NutrientValue potassium;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "calcium"))
    @AttributeOverride(name = "unit", column = @Column(name = "calcium_unit", length = 16))
    private NutrientValue calcium;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "iron"))
    @AttributeOverride(name = "unit", column = @Column(name = "iron_unit", length = 16))
    private NutrientValue iron;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "cholesterol"))
    @AttributeOverride(name = "unit", column = @Column(name = "cholesterol_unit", length = 16))
    private NutrientValue cholesterol;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "vitamin_c"))
    @AttributeOverride(name = "unit", column = @Column(name = "vitamin_c_unit", length = 16))
    private NutrientValue vitaminC;
}
