package com.fdiet.food.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One generic food of the Spanish food composition database, with its
 * composition per 100 g of edible portion.
 *
 * <p>This is the half of the catalogue a diet is actually written in.
 * {@link FoodItem} holds branded commercial products keyed on EAN
 * ({@code BEKIND BARRA CEREAL CARAMELO ALMENDRA Y SAL}); this holds
 * {@code Lechuga}, {@code Aguacate}, {@code Pollo, pechuga, plancha} — the
 * words a nutritionist writes.
 *
 * <p>The id is the {@code f_id} the source assigns, so a sync is repeatable.
 * Every figure is stored exactly as published, with its own unit beside it;
 * see BEDCA-ATTRIBUTION.txt for why nothing here may be normalised.
 *
 * <p>Only the components a diet is read by are stored. The other 33 the source
 * carries stay in bedca_foods.csv, and adding one is a column pair, a field
 * here and a line in the importer's component list.
 */
@Entity
@Table(name = "bedca_foods")
@Getter
@Setter
public class BedcaFood implements CompositionFigures {

    /** The source's own {@code f_id}: assigned, never generated. */
    @Id
    private Long id;

    /** {@code f_ori_name} — the Spanish name, what an ingredient is matched against. */
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "english_name", length = 255)
    private String englishName;

    @Column(name = "scientific_name", length = 255)
    private String scientificName;

    /** {@code namelevel1} / {@code namelevel2}; the source fills them for 182 of the 957 foods. */
    @Column(name = "food_group", length = 255)
    private String foodGroup;

    @Column(name = "food_subgroup", length = 255)
    private String foodSubgroup;

    /** {@code f_origen}: BEDCA or BEDCA2, a property of the source's own filtering. */
    @Column(name = "origin", length = 16)
    private String origin;

    /** The edible fraction, 1.0 when the whole food is edible. */
    @Column(name = "edible_portion", precision = 8, scale = 6)
    private BigDecimal ediblePortion;

    /** ENERC — kJ for 947 of the 957 foods, kcal for 8. Read the unit. */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "energy"))
    @AttributeOverride(name = "unit", column = @Column(name = "energy_unit", length = 16))
    private NutrientValue energy;

    /** PROT */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "protein"))
    @AttributeOverride(name = "unit", column = @Column(name = "protein_unit", length = 16))
    private NutrientValue protein;

    /** FAT */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "fat"))
    @AttributeOverride(name = "unit", column = @Column(name = "fat_unit", length = 16))
    private NutrientValue fat;

    /** FASAT */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "saturated_fat"))
    @AttributeOverride(name = "unit", column = @Column(name = "saturated_fat_unit", length = 16))
    private NutrientValue saturatedFat;

    /** CHO */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "carbohydrates"))
    @AttributeOverride(name = "unit", column = @Column(name = "carbohydrates_unit", length = 16))
    private NutrientValue carbohydrates;

    /** SUGAR — published for only 205 foods. */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "sugars"))
    @AttributeOverride(name = "unit", column = @Column(name = "sugars_unit", length = 16))
    private NutrientValue sugars;

    /** FIBT */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "fiber"))
    @AttributeOverride(name = "unit", column = @Column(name = "fiber_unit", length = 16))
    private NutrientValue fiber;

    /** WATER */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "water"))
    @AttributeOverride(name = "unit", column = @Column(name = "water_unit", length = 16))
    private NutrientValue water;

    /** NA */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "sodium"))
    @AttributeOverride(name = "unit", column = @Column(name = "sodium_unit", length = 16))
    private NutrientValue sodium;

    /** K */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "potassium"))
    @AttributeOverride(name = "unit", column = @Column(name = "potassium_unit", length = 16))
    private NutrientValue potassium;

    /** CA */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "calcium"))
    @AttributeOverride(name = "unit", column = @Column(name = "calcium_unit", length = 16))
    private NutrientValue calcium;

    /** FE */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "iron"))
    @AttributeOverride(name = "unit", column = @Column(name = "iron_unit", length = 16))
    private NutrientValue iron;

    /** CHORL */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "cholesterol"))
    @AttributeOverride(name = "unit", column = @Column(name = "cholesterol_unit", length = 16))
    private NutrientValue cholesterol;

    /** VITC */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "vitamin_c"))
    @AttributeOverride(name = "unit", column = @Column(name = "vitamin_c_unit", length = 16))
    private NutrientValue vitaminC;
}
