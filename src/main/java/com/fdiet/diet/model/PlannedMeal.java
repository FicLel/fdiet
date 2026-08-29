package com.fdiet.diet.model;

import com.fdiet.diet.dto.MealType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/**
 * One meal of a stored {@link DietPlan}: the day it falls on, the slot it
 * occupies in that day, and the dishes it is made of.
 *
 * <p>{@code uk_diet_meals_slot} keeps a slot filled once per day, which is the
 * same rule {@link com.fdiet.diet.domain.Meal} enforces in memory — stated
 * twice on purpose, so a bad write cannot get past the database either.
 */
@Entity
@Table(name = "diet_meals")
@Getter
@Setter
public class PlannedMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diet_id", nullable = false)
    private DietPlan diet;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 16, nullable = false)
    private DayOfWeek dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 24, nullable = false)
    private MealType type;

    /** A free label for the meal — the slot is what {@link #type} carries. */
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @OneToMany(mappedBy = "meal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PlannedDish> dishes = new ArrayList<>();

    protected PlannedMeal() {
    }

    public PlannedMeal(DayOfWeek dayOfWeek, MealType type, String name) {
        this.dayOfWeek = dayOfWeek;
        this.type = type;
        this.name = name;
    }

    public void addDish(PlannedDish dish) {
        dish.setPosition(dishes.size());
        dishes.add(dish);
        dish.setMeal(this);
    }
}
