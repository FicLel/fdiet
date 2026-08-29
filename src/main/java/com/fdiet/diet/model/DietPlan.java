package com.fdiet.diet.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A stored diet. There is one {@link DietStatus#ACTIVE} diet at a time and a
 * record of the archived ones behind it, so this is what a lookup asks for
 * first: the diet in force today.
 *
 * <p>The table also carries an {@code active_flag} generated column — 1 while
 * the row is active, NULL once it is archived — under a unique index, and
 * MySQL allows a unique index to hold many NULLs. That is what keeps a second
 * active diet from ever being inserted. It is deliberately not mapped here:
 * Hibernate validates only the columns it knows about, so leaving it out means
 * no insert can ever try to write it.
 *
 * <p>Day and meal-slot ordering is not a concern of this class. The meals come
 * back in whatever order the database hands them over and are ordered by
 * feeding them through {@link com.fdiet.diet.domain.Diet}, whose {@code
 * EnumMap}s exist for exactly that.
 */
@Entity
@Table(name = "diets")
@Getter
@Setter
public class DietPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private DietStatus status;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    /** Null while the diet is active; the day it was archived once it is not. */
    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "diet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlannedMeal> meals = new ArrayList<>();

    public void addMeal(PlannedMeal meal) {
        meals.add(meal);
        meal.setDiet(this);
    }

    public void removeMeal(PlannedMeal meal) {
        meals.remove(meal);
        meal.setDiet(null);
    }

    /** Stamps the creation time when the caller did not set one. */
    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
