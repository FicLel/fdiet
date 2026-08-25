package com.fdiet.diet.domain;

import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.exception.InvalidDietException;

import java.util.EnumMap;
import java.util.List;

/**
 * One day's worth of meals, keyed by the slot each one occupies. A slot holds a single meal, so the
 * rule is structural — but building from a request is where a duplicate has to become an error
 * rather than a silent overwrite, and that is what the {@link DietDay} constructor is for. {@link
 * #put} overwrites, because that is what editing means.
 *
 * <p>The map iterates in {@link MealType} declaration order, so {@link #asList()} always comes back
 * ordered by slot whatever order the caller supplied.
 */
public class Meal {

    private final EnumMap<MealType, MealDto> byType;

    public Meal() {
        this.byType = new EnumMap<>(MealType.class);
    }

    /** Rejects a slot filled twice; the day is carried only to name it in the error. */
    public Meal(DietDay day) {
        this();
        for (MealDto meal : day.meals()) {
            if (byType.containsKey(meal.type())) {
                throw new InvalidDietException("Repeated meal in " + day.day() + ": " + meal.type());
            }
            byType.put(meal.type(), meal);
        }
    }

    /** The meal in that slot, or {@code null} when it is empty. */
    public MealDto get(MealType type) {
        return byType.get(type);
    }

    /** Puts the meal in its slot, replacing whatever was there. */
    public void put(MealDto meal) {
        byType.put(meal.type(), meal);
    }

    /** Removes that slot's meal and returns it, or {@code null} when there was none. */
    public MealDto remove(MealType type) {
        return byType.remove(type);
    }

    /** The meals held, in {@link MealType} order. */
    public List<MealDto> asList() {
        return List.copyOf(byType.values());
    }
}
