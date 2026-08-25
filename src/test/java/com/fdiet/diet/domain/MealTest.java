package com.fdiet.diet.domain;

import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.exception.InvalidDietException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MealTest {

    @Test
    void startsEmpty() {
        assertThat(new Meal().asList()).isEmpty();
        assertThat(new Meal().get(MealType.LUNCH)).isNull();
    }

    @Test
    void rejectsASlotFilledTwiceNamingTheDay() {
        DietDay day = day(DayOfWeek.MONDAY,
                meal(MealType.DINNER, "Cena ligera"),
                meal(MealType.DINNER, "Segunda cena"));

        assertThatThrownBy(() -> new Meal(day))
                .isInstanceOf(InvalidDietException.class)
                .hasMessage("Repeated meal in MONDAY: DINNER");
    }

    @Test
    void readsBackWhatItWasBuiltWith() {
        MealDto breakfast = meal(MealType.BREAKFAST, "Tostadas");
        Meal meals = new Meal(day(DayOfWeek.MONDAY, breakfast));

        assertThat(meals.get(MealType.BREAKFAST)).isEqualTo(breakfast);
        assertThat(meals.get(MealType.DINNER)).isNull();
    }

    @Test
    void putOverwritesTheSlot() {
        Meal meals = new Meal();
        meals.put(meal(MealType.LUNCH, "Lentejas"));

        MealDto replacement = meal(MealType.LUNCH, "Garbanzos");
        meals.put(replacement);

        assertThat(meals.asList()).containsExactly(replacement);
    }

    @Test
    void removeReturnsWhatItTookAndThenNull() {
        MealDto dinner = meal(MealType.DINNER, "Cena ligera");
        Meal meals = new Meal(day(DayOfWeek.MONDAY, dinner));

        assertThat(meals.remove(MealType.DINNER)).isEqualTo(dinner);
        assertThat(meals.remove(MealType.DINNER)).isNull();
        assertThat(meals.asList()).isEmpty();
    }

    @Test
    void listsInMealTypeOrderWhateverOrderTheyCameIn() {
        MealDto dinner = meal(MealType.DINNER, "Sopa");
        MealDto breakfast = meal(MealType.BREAKFAST, "Tostadas");
        MealDto lunch = meal(MealType.LUNCH, "Lentejas");

        Meal meals = new Meal(day(DayOfWeek.MONDAY, dinner, breakfast, lunch));

        assertThat(meals.asList()).containsExactly(breakfast, lunch, dinner);
    }

    @Test
    void asListIsUnmodifiable() {
        Meal meals = new Meal(day(DayOfWeek.MONDAY, meal(MealType.BREAKFAST, "Tostadas")));

        assertThatThrownBy(() -> meals.asList().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static DietDay day(DayOfWeek dayOfWeek, MealDto... meals) {
        return new DietDay(dayOfWeek, List.of(meals));
    }

    private static MealDto meal(MealType type, String name) {
        return new MealDto(type, name, List.of());
    }
}
