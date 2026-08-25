package com.fdiet.food.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A top-level food category. The id is the {@code IdCategoria} column of
 * fooddata.csv, so it is assigned rather than generated.
 */
@Entity
@Table(name = "food_category")
@Getter
@Setter
public class Category {
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "category", length = 255, nullable = false)
    private String category;

    protected Category() {
    }

    public Category(Long id, String category) {
        this.id = id;
        this.category = category;
    }
}
