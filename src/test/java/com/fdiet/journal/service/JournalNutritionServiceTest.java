package com.fdiet.journal.service;

import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.helpers.PortionScaler;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.model.NutrientValue;
import com.fdiet.food.service.NutritionService;
import com.fdiet.journal.model.ExtraFood;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The off-plan side of the day is totalled by the same rules the plan is, so
 * these are deliberately the questions
 * {@link com.fdiet.diet.service.DietNutritionServiceTest} asks, over this
 * context's own rows. If the two ever disagreed about what a millilitre or an
 * unmatched entry means, a day's figures would stop adding up.
 */
class JournalNutritionServiceTest {

    private final JournalNutritionService nutrition =
            new JournalNutritionService(new NutritionService(), new PortionScaler());

    @Test
    void scalesTheHundredGramFigureToTheQuantityLogged() {
        // 302 kcal per 100 g of ice cream, 79 g of it eaten.
        NutritionDto scaled = nutrition.of(branded(magnum(), "79", "g"));

        assertThat(scaled.energyKcal()).isEqualByComparingTo("238.58");
    }

    @Test
    void readsTheCompositionDatabaseHalfToo() {
        // Lechuga: 65.125 kJ and 1.125 g of protein per 100 g, 80 gr of it.
        NutritionDto scaled = nutrition.of(generic(lechuga(), "80", "gr"));

        assertThat(scaled.energyKcal()).isEqualByComparingTo("12.45");
        assertThat(scaled.proteinG()).isEqualByComparingTo("0.90");
    }

    @Test
    void weighsMillilitresAsGrams() {
        assertThat(nutrition.of(generic(lechuga(), "300", "mL")).proteinG())
                .isEqualByComparingTo("3.38");
    }

    @Test
    void refusesToGuessWhatAUnitWeighs() {
        assertThat(nutrition.of(branded(magnum(), "1", "unidad"))).isNull();
    }

    @Test
    void weighsAHouseholdMeasureTheWayThePlanDoes() {
        // Two spoons of 10 ml each, 20 g of lettuce as far as the arithmetic goes.
        ExtraFood spoons = generic(lechuga(), "2", "cucharada sopera");
        spoons.setFoodMeasure(spoon());

        assertThat(nutrition.of(spoons).proteinG()).isEqualByComparingTo("0.23");
        NutritionSummaryDto summary = nutrition.summarise(List.of(spoons));
        assertThat(summary.counted()).isEqualTo(1);
        assertThat(summary.countedByMeasure()).isEqualTo(1);
    }

    @Test
    void aMeasureForAnotherUnitWeighsNothing() {
        ExtraFood slices = generic(lechuga(), "2", "loncha");
        slices.setFoodMeasure(spoon());

        assertThat(nutrition.of(slices)).isNull();
    }

    @Test
    void hasNothingToSayAboutAnEntryNobodyMatched() {
        assertThat(nutrition.of(unmatched("un trozo de tarta"))).isNull();
    }

    @Test
    void countsEveryEntryIntoExactlyOneOfTheThreeBuckets() {
        List<ExtraFood> extras = List.of(
                branded(magnum(), "79", "g"),      // counted
                generic(lechuga(), "80", "gr"),    // counted
                branded(magnum(), "1", "unidad"),  // matched, unweighable
                unmatched("un trozo de tarta"));   // matched to nothing

        NutritionSummaryDto summary = nutrition.summarise(extras);

        assertThat(summary.ingredients()).isEqualTo(4);
        assertThat(summary.counted()).isEqualTo(2);
        assertThat(summary.unmeasured()).isEqualTo(1);
        assertThat(summary.unmatched()).isEqualTo(1);
        assertThat(summary.counted() + summary.unmeasured() + summary.unmatched())
                .isEqualTo(summary.ingredients());
        // 238.58 + 12.45
        assertThat(summary.totals().energyKcal()).isEqualByComparingTo("251.03");
        assertThat(summary.complete()).isFalse();
    }

    @Test
    void leavesADayOfUnmatchedEntriesBlankRatherThanZero() {
        NutritionSummaryDto summary = nutrition.summarise(List.of(
                unmatched("un trozo de tarta"),
                unmatched("dos cervezas")));

        assertThat(summary.unmatched()).isEqualTo(2);
        assertThat(summary.totals().isEmpty()).isTrue();
    }

    private static CompositionFood lechuga() {
        CompositionFood food = new CompositionFood();
        food.setId(2399L);
        food.setNameEs("Lechuga");
        food.setEnergy(new NutrientValue(new BigDecimal("65.125"), "kJ"));
        food.setProtein(new NutrientValue(new BigDecimal("1.125"), "g"));
        return food;
    }

    private static FoodItem magnum() {
        FoodItem item = new FoodItem();
        item.setId(41L);
        item.setCommercialName("MAGNUM CLASSIC BOMBON HELADO INDIVIDUAL");
        item.setBrand("FRIGO");
        item.setEnergyKcal(new BigDecimal("302"));
        return item;
    }

    private static ReferenceFoodMeasure spoon() {
        ReferenceFoodMeasure measure = new ReferenceFoodMeasure();
        measure.setMeasure(HouseholdMeasure.CUCHARADA_SOPERA);
        measure.setCount(BigDecimal.ONE);
        measure.setMlMin(BigDecimal.TEN);
        measure.setMlMax(BigDecimal.TEN);
        measure.setWeightBasis(WeightBasis.UNSPECIFIED);
        return measure;
    }

    private static ExtraFood generic(CompositionFood food, String quantity, String unit) {
        return new ExtraFood(1L, DayOfWeek.TUESDAY, "lechuga",
                new BigDecimal(quantity), unit, food, null);
    }

    private static ExtraFood branded(FoodItem item, String quantity, String unit) {
        return new ExtraFood(1L, DayOfWeek.TUESDAY, "magnum",
                new BigDecimal(quantity), unit, null, item);
    }

    private static ExtraFood unmatched(String name) {
        return new ExtraFood(1L, DayOfWeek.TUESDAY, name,
                new BigDecimal("1"), "unidad", null, null);
    }
}
