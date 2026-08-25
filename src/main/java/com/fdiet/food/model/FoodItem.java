package com.fdiet.food.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One product of the food catalogue, keyed in the source data by its EAN.
 *
 * <p>The current fooddata.csv only fills a subset of these columns; the rest
 * are kept for richer exports of the same dataset and stay null after an
 * import.
 */
@Entity
@Table(name = "food_items")
@Getter
@Setter
public class FoodItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory")
    private SubCategory subcategory;

    @Column(name = "year_value")
    private Integer year;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(name = "market_share_total_ean", precision = 18, scale = 10)
    private BigDecimal marketShareTotalEan;

    @Column(name = "ean", length = 32, nullable = false, unique = true)
    private String ean;

    @Column(name = "commercial_name", length = 500)
    private String commercialName;

    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    @Column(name = "brand", length = 255)
    private String brand;

    @Column(name = "subbrand", length = 255)
    private String subbrand;

    @Column(name = "legal_name", length = 500)
    private String legalName;

    @Lob
    @Column(name = "ingredients", length = Integer.MAX_VALUE)
    private String ingredients;

    @Column(name = "portion_size_g", precision = 10, scale = 2)
    private BigDecimal portionSizeG;

    @Column(name = "energy_kj", precision = 10, scale = 2)
    private BigDecimal energyKj;

    @Column(name = "energy_kcal", precision = 10, scale = 2)
    private BigDecimal energyKcal;

    @Column(name = "fat_g", precision = 10, scale = 2)
    private BigDecimal fatG;

    @Column(name = "saturated_fat_g", precision = 10, scale = 2)
    private BigDecimal saturatedFatG;

    @Column(name = "carbohydrates_g", precision = 10, scale = 2)
    private BigDecimal carbohydratesG;

    @Column(name = "sugars_g", precision = 10, scale = 2)
    private BigDecimal sugarsG;

    @Column(name = "proteins_g", precision = 10, scale = 2)
    private BigDecimal proteinsG;

    @Column(name = "salt_g", precision = 10, scale = 2)
    private BigDecimal saltG;

    @Column(name = "sodium_g", precision = 10, scale = 2)
    private BigDecimal sodiumG;

    @Column(name = "monounsaturated_fat_g", precision = 10, scale = 2)
    private BigDecimal monounsaturatedFatG;

    @Column(name = "polyunsaturated_fat_g", precision = 10, scale = 2)
    private BigDecimal polyunsaturatedFatG;

    @Column(name = "starch_g", precision = 10, scale = 2)
    private BigDecimal starchG;

    @Column(name = "fiber_g", precision = 10, scale = 2)
    private BigDecimal fiberG;

    @Column(name = "polyols_g", precision = 10, scale = 2)
    private BigDecimal polyolsG;

    @Column(name = "sweeteners", length = 50)
    private String sweeteners;
}
