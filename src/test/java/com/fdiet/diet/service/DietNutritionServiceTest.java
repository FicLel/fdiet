package com.fdiet.diet.service;

import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.helpers.PortionScaler;
import com.fdiet.diet.model.PlannedIngredient;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.NutrientValue;
import com.fdiet.food.service.NutritionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DietNutritionServiceTest {

    private final DietNutritionService nutrition =
            new DietNutritionService(new NutritionService(), new PortionScaler());

    @Test
    void scalesTheHundredGramFigureToTheQuantityWritten() {
        // Lechuga: 65.125 kJ and 1.125 g of protein per 100 g, 80 gr of it.
        PlannedIngredient lechuga = ingredient(lechuga(), "80", "gr");

        NutritionDto scaled = nutrition.of(lechuga);

        assertThat(scaled.energyKcal()).isEqualByComparingTo("12.45");
        assertThat(scaled.proteinG()).isEqualByComparingTo("0.90");
    }

    @Test
    void weighsMillilitresAsGrams() {
        PlannedIngredient broth = ingredient(lechuga(), "300", "mL");

        assertThat(nutrition.of(broth).proteinG()).isEqualByComparingTo("3.38");
    }

    @Test
    void refusesToGuessWhatAUnitWeighs() {
        assertThat(nutrition.of(ingredient(lechuga(), "1", "unidad"))).isNull();
        assertThat(nutrition.of(ingredient(lechuga(), "1", "cdta"))).isNull();
    }

    @Test
    void hasNothingToSayAboutAnIngredientNobodyMatched() {
        assertThat(nutrition.of(ingredient(null, "80", "gr"))).isNull();
    }

    @Test
    void countsEveryIngredientIntoExactlyOneOfTheThreeBuckets() {
        List<PlannedIngredient> ingredients = List.of(
                ingredient(lechuga(), "80", "gr"),      // counted
                ingredient(lechuga(), "100", "gr"),     // counted
                ingredient(lechuga(), "1", "unidad"),   // matched, unweighable
                ingredient(null, "80", "gr"));          // not matched yet

        NutritionSummaryDto summary = nutrition.summarise(ingredients);

        assertThat(summary.ingredients()).isEqualTo(4);
        assertThat(summary.counted()).isEqualTo(2);
        assertThat(summary.unmeasured()).isEqualTo(1);
        assertThat(summary.unmatched()).isEqualTo(1);
        assertThat(summary.counted() + summary.unmeasured() + summary.unmatched())
                .isEqualTo(summary.ingredients());
        // 12.45 + 15.57
        assertThat(summary.totals().energyKcal()).isEqualByComparingTo("28.02");
        assertThat(summary.complete()).isFalse();
    }

    @Test
    void saysSoWhenEveryIngredientContributed() {
        NutritionSummaryDto summary = nutrition.summarise(List.of(
                ingredient(lechuga(), "80", "gr"),
                ingredient(lechuga(), "20", "gr")));

        assertThat(summary.complete()).isTrue();
    }

    @Test
    void leavesTheTotalsOfAnUnmatchedWeekBlankRatherThanZero() {
        NutritionSummaryDto summary = nutrition.summarise(List.of(
                ingredient(null, "80", "gr"),
                ingredient(null, "100", "gr")));

        assertThat(summary.unmatched()).isEqualTo(2);
        assertThat(summary.totals().isEmpty()).isTrue();
    }

    private static BedcaFood lechuga() {
        BedcaFood food = new BedcaFood();
        food.setId(2399L);
        food.setName("Lechuga");
        food.setEnergy(new NutrientValue(new BigDecimal("65.125"), "kJ"));
        food.setProtein(new NutrientValue(new BigDecimal("1.125"), "g"));
        return food;
    }

    private static PlannedIngredient ingredient(BedcaFood food, String quantity, String unit) {
        return new PlannedIngredient("lechuga", null, food, new BigDecimal(quantity), unit);
    }
}
