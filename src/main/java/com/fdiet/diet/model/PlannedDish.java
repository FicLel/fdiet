package com.fdiet.diet.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One plate of a {@link PlannedMeal}, made of catalogue foods.
 *
 * <p>{@code position} is an ordinary column the caller fills in, not an
 * {@code @OrderColumn}: on a {@code mappedBy} collection Hibernate writes that
 * kind of index with a follow-up UPDATE, inserting a null first, which a NOT
 * NULL column rejects.
 */
@Entity
@Table(name = "diet_dishes")
@Getter
@Setter
public class PlannedDish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id", nullable = false)
    private PlannedMeal meal;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /** Its place in the meal, from 0. Unique within the meal. */
    @Column(name = "position", nullable = false)
    private int position;

    @OneToMany(mappedBy = "dish", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PlannedIngredient> ingredients = new ArrayList<>();

    protected PlannedDish() {
    }

    public PlannedDish(String name) {
        this.name = name;
    }

    public void addIngredient(PlannedIngredient ingredient) {
        ingredient.setPosition(ingredients.size());
        ingredients.add(ingredient);
        ingredient.setDish(this);
    }
}
