package com.fdiet.diet.service;

import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.helpers.PortionScaler;
import com.fdiet.diet.model.PlannedIngredient;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.NutrientValue;
import com.fdiet.food.service.NutritionService;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.model.ReferenceFoodMeasure;
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
    void weighsARangeOnlyOnceSomebodySettlesIt() {
        PlannedIngredient ranged = ingredient(lechuga(), "40", "gr");
        ranged.setQuantityMax(new BigDecimal("60"));

        assertThat(nutrition.of(ranged)).isNull();
        assertThat(nutrition.edibleGrams(ranged)).isNull();
        assertThat(nutrition.summarise(List.of(ranged)).unmeasured()).isEqualTo(1);

        ranged.setQuantityMax(null);
        assertThat(nutrition.edibleGrams(ranged)).isEqualByComparingTo("40");
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

    @Test
    void weighsAHouseholdMeasureThroughTheRowAttachedToIt() {
        // AESAN 2022: 1 cucharada sopera of olive oil is 10 ml, read as 10 g.
        PlannedIngredient oil = ingredient(lechuga(), "2", "cda");
        oil.setFoodMeasure(measure(HouseholdMeasure.CUCHARADA_SOPERA, null, "10", "1", WeightBasis.UNSPECIFIED));

        assertThat(nutrition.edibleGrams(oil)).isEqualByComparingTo("20");
        // 65.125 kJ per 100 g, 20 g of it.
        assertThat(nutrition.of(oil).energyKcal()).isEqualByComparingTo("3.11");
    }

    @Test
    void dividesAPublishedCountDownToOneMeasure() {
        // 5 al día: "3 Uds. medianas" of apricot are 180 g, so one is 60 g.
        PlannedIngredient apricots = ingredient(lechuga(), "2", "unidades");
        apricots.setFoodMeasure(measure(HouseholdMeasure.UNIDAD, "180", null, "3", WeightBasis.NET_EDIBLE));

        assertThat(nutrition.edibleGrams(apricots)).isEqualByComparingTo("120");
    }

    @Test
    void cutsAGrossMeasureToTheEdiblePartAndRefusesWithoutOne() {
        BedcaFood kiwi = lechuga();
        kiwi.setEdiblePortion(new BigDecimal("0.85"));
        PlannedIngredient piece = ingredient(kiwi, "1", "unidad");
        piece.setFoodMeasure(measure(HouseholdMeasure.UNIDAD, "100", null, "1", WeightBasis.GROSS));

        assertThat(nutrition.edibleGrams(piece)).isEqualByComparingTo("85");

        kiwi.setEdiblePortion(null);
        assertThat(nutrition.edibleGrams(piece)).isNull();
    }

    @Test
    void aRangeWeighsNothing() {
        PlannedIngredient egg = ingredient(lechuga(), "1", "unidad");
        ReferenceFoodMeasure range = measure(HouseholdMeasure.UNIDAD, "53", null, "1", WeightBasis.UNSPECIFIED);
        range.setGramsMax(new BigDecimal("63"));
        egg.setFoodMeasure(range);

        assertThat(nutrition.of(egg)).isNull();
    }

    @Test
    void aMeasureOnlyWeighsTheUnitItMeasures() {
        PlannedIngredient slices = ingredient(lechuga(), "2", "lonchas");
        slices.setFoodMeasure(measure(HouseholdMeasure.CUCHARADA_SOPERA, null, "10", "1", WeightBasis.UNSPECIFIED));
        assertThat(nutrition.of(slices)).isNull();

        // A weight is a weight, whatever measure the ingredient once had.
        PlannedIngredient grams = ingredient(lechuga(), "80", "gr");
        grams.setFoodMeasure(measure(HouseholdMeasure.CUCHARADA_SOPERA, null, "10", "1", WeightBasis.UNSPECIFIED));
        assertThat(nutrition.edibleGrams(grams)).isEqualByComparingTo("80");
    }

    @Test
    void saysHowMuchOfTheTotalRestsOnAHouseholdMeasure() {
        PlannedIngredient oil = ingredient(lechuga(), "1", "cdta");
        oil.setFoodMeasure(measure(HouseholdMeasure.CUCHARADITA, null, "5", "1", WeightBasis.UNSPECIFIED));

        NutritionSummaryDto summary = nutrition.summarise(List.of(
                ingredient(lechuga(), "80", "gr"), oil, ingredient(lechuga(), "1", "cda")));

        assertThat(summary.counted()).isEqualTo(2);
        assertThat(summary.countedByMeasure()).isEqualTo(1);
        assertThat(summary.unmeasured()).isEqualTo(1);
        assertThat(summary.counted() + summary.unmeasured() + summary.unmatched())
                .isEqualTo(summary.ingredients());
    }

    private static ReferenceFoodMeasure measure(HouseholdMeasure household, String grams, String ml,
                                                String count, WeightBasis basis) {
        ReferenceFoodMeasure measure = new ReferenceFoodMeasure();
        measure.setMeasure(household);
        measure.setCount(new BigDecimal(count));
        if (grams != null) {
            measure.setGramsMin(new BigDecimal(grams));
            measure.setGramsMax(new BigDecimal(grams));
        }
        if (ml != null) {
            measure.setMlMin(new BigDecimal(ml));
            measure.setMlMax(new BigDecimal(ml));
        }
        measure.setWeightBasis(basis);
        return measure;
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
