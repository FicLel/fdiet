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

class DietTest {

    @Test
    void acceptsAnEmptyDiet() {
        assertThat(new Diet(List.of()).days()).isEmpty();
        assertThat(new Diet().days()).isEmpty();
    }

    @Test
    void acceptsADayWithOneMealPerType() {
        DietDay monday = day(DayOfWeek.MONDAY,
                meal(MealType.BREAKFAST, "Tostadas"),
                meal(MealType.LUNCH, "Lentejas"),
                meal(MealType.DINNER, "Cena ligera"));

        assertThat(new Diet(List.of(monday)).days()).containsExactly(monday);
    }

    @Test
    void rejectsTwoMealsOfTheSameTypeInADay() {
        List<DietDay> diet = List.of(day(DayOfWeek.MONDAY,
                meal(MealType.DINNER, "Cena ligera"),
                meal(MealType.DINNER, "Segunda cena")));

        assertThatThrownBy(() -> new Diet(diet))
                .isInstanceOf(InvalidDietException.class)
                .hasMessage("Repeated meal in MONDAY: DINNER");
    }

    @Test
    void acceptsTheSameMealTypeOnDifferentDays() {
        DietDay monday = day(DayOfWeek.MONDAY, meal(MealType.BREAKFAST, "Tostadas"));
        DietDay tuesday = day(DayOfWeek.TUESDAY, meal(MealType.BREAKFAST, "Avena"));

        assertThat(new Diet(List.of(monday, tuesday)).days()).containsExactly(monday, tuesday);
    }

    @Test
    void rejectsARepeatedDay() {
        List<DietDay> diet = List.of(
                day(DayOfWeek.MONDAY, meal(MealType.BREAKFAST, "Tostadas")),
                day(DayOfWeek.MONDAY, meal(MealType.LUNCH, "Lentejas")));

        assertThatThrownBy(() -> new Diet(diet))
                .isInstanceOf(InvalidDietException.class)
                .hasMessage("Repeated day in the diet: MONDAY");
    }

    @Test
    void readsPutsAndRemovesASingleMeal() {
        Diet diet = new Diet(List.of(day(DayOfWeek.MONDAY, meal(MealType.BREAKFAST, "Tostadas"))));

        assertThat(diet.meal(DayOfWeek.MONDAY, MealType.BREAKFAST)).isEqualTo(meal(MealType.BREAKFAST, "Tostadas"));
        assertThat(diet.meal(DayOfWeek.MONDAY, MealType.DINNER)).isNull();

        MealDto dinner = meal(MealType.DINNER, "Cena ligera");
        diet.putMeal(DayOfWeek.MONDAY, dinner);
        assertThat(diet.meal(DayOfWeek.MONDAY, MealType.DINNER)).isEqualTo(dinner);

        assertThat(diet.removeMeal(DayOfWeek.MONDAY, MealType.DINNER)).isEqualTo(dinner);
        assertThat(diet.meal(DayOfWeek.MONDAY, MealType.DINNER)).isNull();
        assertThat(diet.removeMeal(DayOfWeek.MONDAY, MealType.DINNER)).isNull();
    }

    @Test
    void putMealOverwritesTheSlotInsteadOfThrowing() {
        Diet diet = new Diet(List.of(day(DayOfWeek.MONDAY, meal(MealType.DINNER, "Cena ligera"))));

        MealDto replacement = meal(MealType.DINNER, "Segunda cena");
        diet.putMeal(DayOfWeek.MONDAY, replacement);

        assertThat(diet.meals(DayOfWeek.MONDAY)).containsExactly(replacement);
    }

    @Test
    void putMealCreatesADayThatIsNotThereYet() {
        Diet diet = new Diet();
        MealDto breakfast = meal(MealType.BREAKFAST, "Avena");

        diet.putMeal(DayOfWeek.SUNDAY, breakfast);

        assertThat(diet.days()).containsExactly(new DietDay(DayOfWeek.SUNDAY, List.of(breakfast)));
    }

    @Test
    void returnsAnEmptyDayForOneNotFilledIn() {
        Diet diet = new Diet();

        assertThat(diet.day(DayOfWeek.FRIDAY)).isEqualTo(new DietDay(DayOfWeek.FRIDAY, List.of()));
        assertThat(diet.meals(DayOfWeek.FRIDAY)).isEmpty();
        assertThat(diet.days()).isEmpty();
    }

    @Test
    void ordersDaysAndMealsWhateverOrderTheyCameIn() {
        DietDay tuesday = day(DayOfWeek.TUESDAY, meal(MealType.DINNER, "Cena ligera"));
        DietDay monday = day(DayOfWeek.MONDAY,
                meal(MealType.DINNER, "Sopa"),
                meal(MealType.BREAKFAST, "Tostadas"));

        Diet diet = new Diet(List.of(tuesday, monday));

        assertThat(diet.days()).containsExactly(
                day(DayOfWeek.MONDAY, meal(MealType.BREAKFAST, "Tostadas"), meal(MealType.DINNER, "Sopa")),
                tuesday);
    }

    @Test
    void removesAWholeDayAndReplacesAnother() {
        Diet diet = new Diet(List.of(
                day(DayOfWeek.MONDAY, meal(MealType.BREAKFAST, "Tostadas")),
                day(DayOfWeek.TUESDAY, meal(MealType.BREAKFAST, "Avena"))));

        diet.removeDay(DayOfWeek.MONDAY);
        DietDay newTuesday = day(DayOfWeek.TUESDAY, meal(MealType.LUNCH, "Lentejas"));
        diet.putDay(newTuesday);

        assertThat(diet.days()).containsExactly(newTuesday);
    }

    private static DietDay day(DayOfWeek dayOfWeek, MealDto... meals) {
        return new DietDay(dayOfWeek, List.of(meals));
    }

    private static MealDto meal(MealType type, String name) {
        return new MealDto(type, name, List.of());
    }
}
