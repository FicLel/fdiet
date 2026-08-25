package com.fdiet.food.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A subcategory of a {@link Category}. The id is the {@code IdSubcategoria}
 * column of fooddata.csv, which is unique across the whole file.
 */
@Entity
@Table(name = "food_subcategory")
@Getter
@Setter
public class SubCategory {
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "subcategory", length = 255, nullable = false)
    private String subcategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    protected SubCategory() {
    }

    public SubCategory(Long id, String subcategory, Category category) {
        this.id = id;
        this.subcategory = subcategory;
        this.category = category;
    }
}
