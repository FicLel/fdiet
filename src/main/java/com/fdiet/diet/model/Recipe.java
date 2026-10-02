package com.fdiet.diet.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * What goes into a plate and how it is made: the ingredients, in the quantity of
 * one serving, and the preparation in the nutritionist's own words.
 *
 * <p>A {@linkplain #library library} recipe is shared. Every {@link PlannedDish}
 * that points at it reads the same ingredients, so correcting one corrects every
 * week that serves it; a patient who needs more of it gets a larger
 * {@link PlannedDish#getServings() servings} rather than a copy. A private one
 * belongs to the dish it was written in and is replaced with that dish's week.
 *
 * <p>{@code library_key} — the name while the recipe is in the library, NULL
 * otherwise — is a generated column and is not mapped, so no write can set it;
 * the unique index on it is what keeps two library recipes from sharing a name.
 */
@Entity
@Table(name = "recipes")
@Getter
@Setter
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /** How it is made, as the nutritionist wrote it. Null when nobody has. */
    @Column(name = "steps", length = 4000)
    private String steps;

    /**
     * The ingredients as they were written, when that is known. Reading the text
     * into ingredients is not reversible, so this is what an editor puts back in
     * front of the nutritionist — never a reconstruction from the parts.
     */
    @Column(name = "raw_text", length = 1000)
    private String rawText;

    @Column(name = "library", nullable = false)
    private boolean library;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    protected Recipe() {
    }

    public Recipe(String name, String rawText, String steps, boolean library) {
        this.name = name;
        this.rawText = rawText;
        this.steps = steps;
        this.library = library;
    }

    public void addIngredient(RecipeIngredient ingredient) {
        ingredient.setPosition(ingredients.size());
        ingredients.add(ingredient);
        ingredient.setRecipe(this);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
