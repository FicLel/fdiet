package com.fdiet.food.service;

import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.model.NutrientValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The unit arithmetic on a composition food. CIQUAL and BLS publish energy in
 * kcal, but a figure in kJ, a stray milligram or an unknown unit must still read
 * correctly, so each case is built by hand.
 */
class NutritionServiceTest {

    private final NutritionService nutritionService = new NutritionService();

    @Test
    void readsKilojoulesAsKilocalories() {
        CompositionFood food = new CompositionFood();
        food.setEnergy(new NutrientValue(new BigDecimal("65.125"), "kJ"));

        // 65.125 / 4.184, a figure that does not divide evenly
        assertThat(nutritionService.per100g(food).energyKcal())
                .isEqualByComparingTo("15.565249");
    }

    @Test
    void leavesAFigurePublishedInKilocaloriesAlone() {
        CompositionFood food = new CompositionFood();
        food.setEnergy(new NutrientValue(new BigDecimal("240"), "kcal"));

        assertThat(nutritionService.per100g(food).energyKcal()).isEqualByComparingTo("240");
    }

    @Test
    void convertsTheStrayMilligramRowsToGrams() {
        CompositionFood food = new CompositionFood();
        food.setFiber(new NutrientValue(new BigDecimal("1500"), "mg"));

        assertThat(nutritionService.per100g(food).fiberG()).isEqualByComparingTo("1.5");
    }

    @Test
    void refusesAUnitItDoesNotKnowRatherThanGuessingTheScale() {
        CompositionFood food = new CompositionFood();
        food.setProtein(new NutrientValue(new BigDecimal("12"), "oz"));

        assertThat(nutritionService.per100g(food).proteinG()).isNull();
    }

    @Test
    void leavesAComponentTheSourceNeverPublishedNull() {
        CompositionFood food = new CompositionFood();
        food.setProtein(new NutrientValue(new BigDecimal("1.125"), "g"));

        NutritionDto nutrition = nutritionService.per100g(food);

        assertThat(nutrition.proteinG()).isEqualByComparingTo("1.125");
        // Not zero: the source measured no sugars, it did not measure none.
        assertThat(nutrition.sugarsG()).isNull();
        assertThat(nutrition.isEmpty()).isFalse();
    }

    @Test
    void readsABrandedLabelInTheUnitsItDeclares() {
        FoodItem item = new FoodItem();
        item.setEnergyKcal(new BigDecimal("512"));
        item.setProteinsG(new BigDecimal("6.2"));
        // fooddata.csv fills salt and leaves sodium null.
        item.setSaltG(new BigDecimal("0.5"));

        NutritionDto nutrition = nutritionService.per100g(item);

        assertThat(nutrition.energyKcal()).isEqualByComparingTo("512");
        assertThat(nutrition.proteinG()).isEqualByComparingTo("6.2");
        assertThat(nutrition.sodiumMg()).isEqualByComparingTo("200");
    }

    @Test
    void fallsBackToKilojoulesWhenALabelDeclaresNoKilocalories() {
        FoodItem item = new FoodItem();
        item.setEnergyKj(new BigDecimal("2143"));

        assertThat(nutritionService.per100g(item).energyKcal()).isEqualByComparingTo("512.189293");
    }

    @Test
    void knowsWhenNothingIsKnown() {
        assertThat(nutritionService.per100g((CompositionFood) null).isEmpty()).isTrue();
        assertThat(nutritionService.per100g(new CompositionFood()).isEmpty()).isTrue();
    }

    /** CIQUAL 20031 "Lettuce, raw": energy published in kcal, sodium in mg, energy-less foods blank. */
    @Test
    void readsACiqualFoodTheSameWay() {
        CompositionFood lettuce = new CompositionFood();
        lettuce.setEnergy(new NutrientValue(new BigDecimal("14.7"), "kcal"));
        lettuce.setSodium(new NutrientValue(new BigDecimal("10"), "mg"));

        NutritionDto nutrition = nutritionService.per100g(lettuce);

        assertThat(nutrition.energyKcal()).isEqualByComparingTo("14.7");
        assertThat(nutrition.sodiumMg()).isEqualByComparingTo("10");
        assertThat(nutritionService.per100g(new CompositionFood()).energyKcal()).isNull();
    }
}
