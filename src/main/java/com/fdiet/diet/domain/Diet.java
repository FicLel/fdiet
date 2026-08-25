package com.fdiet.diet.domain;

import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.exception.InvalidDietException;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A weekly diet, keyed by day. Each day's meals belong to a {@link Meal}, which enforces its own
 * one-meal-per-slot rule; all this class polices is that a day appears once in the incoming
 * request, rejecting a repeat rather than letting the later entry overwrite the earlier silently.
 * The mutators do overwrite, because that is what editing means. An empty diet is valid — the user
 * fills the week in over several sittings.
 *
 * <p>The map iterates in {@link DayOfWeek} declaration order, so {@link #days()} always comes back
 * ordered by day, whatever order the caller supplied.
 */
public class Diet {

    private final EnumMap<DayOfWeek, Meal> days;

    public Diet() {
        this.days = new EnumMap<>(DayOfWeek.class);
    }

    public Diet(List<DietDay> diet) {
        this();
        for (DietDay day : diet) {
            if (days.containsKey(day.day())) {
                throw new InvalidDietException("Repeated day in the diet: " + day.day());
            }
            days.put(day.day(), new Meal(day));
        }
    }

    /** The meal in that slot, or {@code null} when the day has none. */
    public MealDto meal(DayOfWeek day, MealType type) {
        Meal meals = days.get(day);
        return meals == null ? null : meals.get(type);
    }

    /** The day's meals in {@link MealType} order; empty when the day has not been filled in. */
    public List<MealDto> meals(DayOfWeek day) {
        Meal meals = days.get(day);
        return meals == null ? List.of() : meals.asList();
    }

    /** Never null: a day that has not been filled in comes back with no meals. */
    public DietDay day(DayOfWeek day) {
        return new DietDay(day, meals(day));
    }

    /** The days that have been filled in, in {@link DayOfWeek} order. */
    public List<DietDay> days() {
        List<DietDay> filled = new ArrayList<>(days.size());
        for (Map.Entry<DayOfWeek, Meal> entry : days.entrySet()) {
            filled.add(new DietDay(entry.getKey(), entry.getValue().asList()));
        }
        return List.copyOf(filled);
    }

    /** Puts the meal in its slot, replacing whatever was there. */
    public void putMeal(DayOfWeek day, MealDto meal) {
        days.computeIfAbsent(day, d -> new Meal()).put(meal);
    }

    /** Removes that slot's meal and returns it, or {@code null} when there was none. */
    public MealDto removeMeal(DayOfWeek day, MealType type) {
        Meal meals = days.get(day);
        return meals == null ? null : meals.remove(type);
    }

    /** Replaces the whole day. Its meals must still be one per slot. */
    public void putDay(DietDay day) {
        days.put(day.day(), new Meal(day));
    }

    public void removeDay(DayOfWeek day) {
        days.remove(day);
    }
}
