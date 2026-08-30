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

    /**
     * The cell as it was written, when it is known.
     *
     * <p>The name and the ingredients are what the sentence was read as, and
     * reading it is not reversible. This is the sentence, so an editor that has
     * to send the whole week back can return the cells it did not touch exactly
     * as they were. Null where nothing wrote one — never a reconstruction,
     * which would be a different sentence dressed up as the original.
     */
    @Column(name = "raw_text", length = 1000)
    private String rawText;

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

    public PlannedDish(String name, String rawText) {
        this.name = name;
        this.rawText = rawText;
    }

    public void addIngredient(PlannedIngredient ingredient) {
        ingredient.setPosition(ingredients.size());
        ingredients.add(ingredient);
        ingredient.setDish(this);
    }
}
