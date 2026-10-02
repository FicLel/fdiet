package com.fdiet.diet.model;

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
import java.util.List;

/**
 * One plate of a {@link PlannedMeal}: what the patient reads it as, and the
 * recipe behind it.
 *
 * <p>{@code name} is the description — "Huevos revueltos" — and nothing reads
 * food out of it. What is on the plate is the {@link Recipe}, served
 * {@code servings} times: a library recipe is written for one serving and shared,
 * so a patient who needs half as much again is given 1.5 of it rather than a copy
 * that would stop following the original. Null recipe is a plate that is only a
 * description ("Comida libre").
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

    /** The description the patient reads. */
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /**
     * No cascade: a library recipe outlives every dish that serves it, and a
     * private one is written and removed by the service that owns recipes.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(name = "servings", precision = 6, scale = 2, nullable = false)
    private BigDecimal servings = BigDecimal.ONE;

    /** Its place in the meal, from 0. Unique within the meal. */
    @Column(name = "position", nullable = false)
    private int position;

    protected PlannedDish() {
    }

    public PlannedDish(String name) {
        this.name = name;
    }

    public PlannedDish(String name, Recipe recipe, BigDecimal servings) {
        this.name = name;
        this.recipe = recipe;
        this.servings = servings == null ? BigDecimal.ONE : servings;
    }

    /** The recipe's ingredients, or none for a description-only plate. */
    public List<RecipeIngredient> getIngredients() {
        return recipe == null ? List.of() : recipe.getIngredients();
    }
}
