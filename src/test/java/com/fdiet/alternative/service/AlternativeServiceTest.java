package com.fdiet.alternative.service;

import com.fdiet.alternative.domain.EquivalenceBasis;
import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.alternative.dto.AlternativeDto;
import com.fdiet.alternative.dto.AlternativeQueryDto;
import com.fdiet.alternative.dto.FoodAlternativesDto;
import com.fdiet.alternative.helpers.FoodCategoriser;
import com.fdiet.alternative.helpers.NutritionSimilarity;
import com.fdiet.food.exception.CompositionFoodNotFoundException;
import com.fdiet.food.helpers.NameMatcher;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.model.NutrientValue;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.NutritionService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.exception.ReferenceNotFoundException;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The catalogue here was a slice of bedca_foods.csv — names and published
 * figures, kilojoules and all — kept as crosswalked composition foods: the
 * shelf and the arithmetic do not care which table a figure came from, and a
 * kilojoule figure still exercises the unit conversion.
 */
class AlternativeServiceTest {

    private static final Long CHICKEN_BREAST = 2297L;
    private static final Long LETTUCE = 2399L;
    private static final Long OIL = 1L;

    private final ICompositionFoodService foods = mock(ICompositionFoodService.class);
    private final IReferenceService reference = mock(IReferenceService.class);

    private final AlternativeService service = new AlternativeService(
            foods,
            new NutritionService(),
            new FoodCategoriser(),
            new NutritionSimilarity(),
            new NameMatcher(),
            reference);

    private List<CompositionFood> catalogue;

    @BeforeEach
    void catalogue() {
        catalogue = List.of(
                // Meats.
                food(CHICKEN_BREAST, "Pollo, pechuga, plancha", "690", "31", "3.6", "0"),
                food(994L, "Pollo, pechuga, con piel, crudo", "705", "30.5", "5.2", "0"),
                food(688L, "Cerdo, lomo, asado", "661", "30", "4.1", "0"),
                food(2000L, "Ternera, solomillo, asado", "728", "32", "6.1", "0"),
                food(770L, "Chorizo", "1770", "17", "38", "2"),
                // Fish — a different shelf, however near the figures.
                food(2100L, "Merluza fresca", "297", "15.9", "0.8", "0"),
                // Vegetables — the shelf that must never be offered for a meat.
                food(LETTUCE, "Lechuga", "65", "1.3", "0.2", "1.4"),
                food(2422L, "Tomate, asado", "80", "1", "0.3", "1.2"),
                // A food the catalogue publishes nothing about.
                blank(3000L, "Cordero, parte sin especificar"),
                // A food no rule claims.
                food(4000L, "Cremoso san millan", "1200", "20", "25", "1"),
                // A fat, so an oil has somewhere to go.
                food(OIL, "Aceite de oliva", "3700", "0", "100", "0"));

        when(foods.entitiesNamed()).thenReturn(catalogue);
        for (CompositionFood food : catalogue) {
            when(foods.entityById(food.getId())).thenReturn(food);
        }
    }

    @Test
    void neverLeavesTheFoodsOwnFamily() {
        FoodAlternativesDto alternatives = service.forFoodId(CHICKEN_BREAST, 10, null, false);

        assertThat(alternatives.category()).isEqualTo(FoodCategory.MEAT);
        assertThat(alternatives.alternatives())
                .extracting(AlternativeDto::name)
                .containsExactlyInAnyOrder("Cerdo, lomo, asado", "Ternera, solomillo, asado", "Chorizo")
                .doesNotContain("Lechuga", "Tomate, asado", "Merluza fresca");
    }

    /**
     * A lettuce can be made to look like a chicken breast on paper — a large
     * enough portion of one meets a small enough portion of the other — which
     * is the whole reason the category decides eligibility and the figures only
     * decide the order.
     */
    @Test
    void offersNoMeatForAVegetable() {
        FoodAlternativesDto alternatives = service.forFoodId(LETTUCE, 10, null, false);

        assertThat(alternatives.category()).isEqualTo(FoodCategory.VEGETABLE);
        assertThat(alternatives.alternatives())
                .extracting(AlternativeDto::name)
                .containsExactly("Tomate, asado");
    }

    @Test
    void putsTheNearestCompositionFirst() {
        assertThat(service.forFoodId(CHICKEN_BREAST, 10, null, false).alternatives())
                .extracting(AlternativeDto::name)
                .startsWith("Cerdo, lomo, asado")
                .endsWith("Chorizo");
    }

    @Test
    void leavesOutAnotherCutOfTheSameFood() {
        assertThat(service.forFoodId(CHICKEN_BREAST, 10, null, false).alternatives())
                .extracting(AlternativeDto::name)
                .doesNotContain("Pollo, pechuga, con piel, crudo");
    }

    @Test
    void keepsThemWhenTheCallerAsksFor() {
        assertThat(service.forFoodId(CHICKEN_BREAST, 10, null, true).alternatives())
                .extracting(AlternativeDto::name)
                .contains("Pollo, pechuga, con piel, crudo");
    }

    @Test
    void neverOffersTheFoodItself() {
        assertThat(service.forFoodId(CHICKEN_BREAST, 10, null, true).alternatives())
                .extracting(AlternativeDto::compositionFoodId)
                .doesNotContain(CHICKEN_BREAST);
    }

    /** The counts are what tell a short list from an unreadable one. */
    @Test
    void saysHowManyWereEligibleAndHowManyCouldBeCompared() {
        FoodAlternativesDto alternatives = service.forFoodId(CHICKEN_BREAST, 10, null, false);

        // Cerdo, Ternera, Chorizo and the lamb nothing is published about.
        assertThat(alternatives.inCategory()).isEqualTo(4);
        assertThat(alternatives.ranked()).isEqualTo(3);
    }

    @Test
    void answersAFoodNoRuleClaimsWithNothingRatherThanAnything() {
        FoodAlternativesDto alternatives = service.forFoodId(4000L, 10, null, false);

        assertThat(alternatives.category()).isNull();
        assertThat(alternatives.categoryLabel()).isNull();
        assertThat(alternatives.alternatives()).isEmpty();
        assertThat(alternatives.inCategory()).isZero();
        assertThat(alternatives.nutrition().energyKcal()).isNotNull();
    }

    /** CIQUAL and BLS publish no Spanish name; without the crosswalk's, there is no family. */
    @Test
    void answersAFoodWithoutASpanishNameWithNoCategory() {
        CompositionFood untranslated = food(6000L, "x", "500", "20", "5", "0");
        untranslated.setNameEs(null);
        untranslated.setNameEn("Chicken, breast, grilled");
        when(foods.entityById(6000L)).thenReturn(untranslated);

        FoodAlternativesDto alternatives = service.forFoodId(6000L, 10, null, false);

        assertThat(alternatives.name()).isEqualTo("Chicken, breast, grilled");
        assertThat(alternatives.category()).isNull();
        assertThat(alternatives.alternatives()).isEmpty();
    }

    @Test
    void keepsToTheLimitAskedFor() {
        assertThat(service.forFoodId(CHICKEN_BREAST, 1, null, false).alternatives()).hasSize(1);
    }

    /**
     * 100 g of grilled chicken breast is 690 kJ, which is 164.9 kcal; the same
     * energy in roast pork loin, at 661 kJ per 100 g, is 104 g of it.
     */
    @Test
    void saysHowMuchOfTheAlternativeCarriesTheSameEnergy() {
        FoodAlternativesDto alternatives =
                service.forFoodId(CHICKEN_BREAST, 10, new BigDecimal("100"), false);

        assertThat(alternatives.grams()).isEqualByComparingTo("100");
        assertThat(alternatives.portion().energyKcal()).isEqualByComparingTo("164.91");

        AlternativeDto pork = alternatives.alternatives().get(0);
        assertThat(pork.name()).isEqualTo("Cerdo, lomo, asado");
        assertThat(pork.equivalentGrams()).isEqualByComparingTo("104");
        assertThat(pork.equivalentPortion().energyKcal()).isEqualByComparingTo("164.30");
    }

    /**
     * 100 g of grilled chicken breast carries 31 g of protein; roast pork loin
     * publishes 30 g per 100 g, so the same protein is 103 g of it.
     */
    @Test
    void holdsAMacronutrientEqualWhenAskedTo() {
        FoodAlternativesDto alternatives = service.forFoodId(CHICKEN_BREAST,
                new AlternativeQueryDto(10, new BigDecimal("100"), false, EquivalenceBasis.PROTEIN, null));

        assertThat(alternatives.basis()).isEqualTo(EquivalenceBasis.PROTEIN);
        AlternativeDto pork = alternatives.alternatives().stream()
                .filter(alternative -> alternative.name().equals("Cerdo, lomo, asado"))
                .findFirst().orElseThrow();
        assertThat(pork.equivalentGrams()).isEqualByComparingTo("103");
        assertThat(pork.equivalentPortion().proteinG()).isEqualByComparingTo("30.90");
    }

    @Test
    void offersNoWeightOfAFoodThatCarriesTooLittleOfTheBasis() {
        // Chicken publishes no carbohydrate at all, so there is nothing to hold equal.
        FoodAlternativesDto alternatives = service.forFoodId(CHICKEN_BREAST,
                new AlternativeQueryDto(10, new BigDecimal("100"), false,
                        EquivalenceBasis.CARBOHYDRATE, null));

        assertThat(alternatives.alternatives()).isNotEmpty()
                .allSatisfy(alternative -> assertThat(alternative.equivalentGrams()).isNull());
    }

    /**
     * CIQUAL publishes no energy for 145 foods. A food like that is still ranked
     * on the figures it does publish, and gets no equivalent weight by energy —
     * a blank, never a guess and never a failure.
     */
    @Test
    void ranksAFoodWithoutEnergyAndOffersItNoEquivalentWeight() {
        CompositionFood noEnergy = blank(5000L, "Cerdo, chuleta, plancha");
        noEnergy.setProtein(new NutrientValue(new BigDecimal("29"), "g"));
        noEnergy.setFat(new NutrientValue(new BigDecimal("9"), "g"));
        noEnergy.setFiber(new NutrientValue(new BigDecimal("0"), "g"));
        List<CompositionFood> withIt = new java.util.ArrayList<>(catalogue);
        withIt.add(noEnergy);
        when(foods.entitiesNamed()).thenReturn(withIt);

        FoodAlternativesDto alternatives =
                service.forFoodId(CHICKEN_BREAST, 10, new BigDecimal("100"), false);

        AlternativeDto chop = alternatives.alternatives().stream()
                .filter(alternative -> alternative.name().equals("Cerdo, chuleta, plancha"))
                .findFirst().orElseThrow();
        assertThat(chop.nutrition().energyKcal()).isNull();
        assertThat(chop.equivalentGrams()).isNull();
        assertThat(chop.equivalentPortion()).isNull();
    }

    @Test
    void answersForAReferenceFoodWithoutEnergy() {
        CompositionFood noEnergy = blank(5001L, "Pollo, muslo, asado");
        noEnergy.setProtein(new NutrientValue(new BigDecimal("27"), "g"));
        noEnergy.setFat(new NutrientValue(new BigDecimal("8"), "g"));
        when(foods.entityById(5001L)).thenReturn(noEnergy);

        FoodAlternativesDto alternatives = service.forFoodId(5001L, 10, new BigDecimal("100"), false);

        assertThat(alternatives.portion().energyKcal()).isNull();
        assertThat(alternatives.alternatives()).isNotEmpty()
                .allSatisfy(alternative -> assertThat(alternative.equivalentGrams()).isNull());
    }

    /** 104 g of pork against a 100-125 g ration is 0,8 to 1,0 rations, sent as a range. */
    @Test
    void readsTheEquivalentWeightInTheProfilesRations() {
        when(reference.profileExists("AESAN-2022:ADULTOS")).thenReturn(true);
        when(reference.countingRation(eq("AESAN-2022:ADULTOS"), any(), anyString()))
                .thenReturn(meatRation(FoodState.UNSPECIFIED));

        FoodAlternativesDto alternatives = service.forFoodId(CHICKEN_BREAST,
                new AlternativeQueryDto(10, new BigDecimal("100"), false, null, "AESAN-2022:ADULTOS"));

        assertThat(alternatives.portionRations().rationsMin()).isEqualByComparingTo("0.8");
        assertThat(alternatives.portionRations().rationsMax()).isEqualByComparingTo("1.0");
        AlternativeDto pork = alternatives.alternatives().get(0);
        assertThat(pork.rations().groupLabel()).isEqualTo("Carnes");
        assertThat(pork.rations().rationsMin()).isEqualByComparingTo("0.8");
        assertThat(pork.rations().rationsMax()).isEqualByComparingTo("1.0");
        assertThat(pork.rations().sourceShortName()).isEqualTo("AESAN 2022");
    }

    @Test
    void countsNoRationDefinedInAnotherState() {
        when(reference.profileExists("AESAN-2022:ADULTOS")).thenReturn(true);
        when(reference.countingRation(eq("AESAN-2022:ADULTOS"), any(), anyString()))
                .thenReturn(meatRation(FoodState.RAW));

        FoodAlternativesDto alternatives = service.forFoodId(CHICKEN_BREAST,
                new AlternativeQueryDto(10, new BigDecimal("100"), false, null, "AESAN-2022:ADULTOS"));

        // Grilled chicken against a raw-weight ration is a yield nobody chose.
        assertThat(alternatives.portionRations()).isNull();
    }

    @Test
    void refusesAProfileNobodyLoaded() {
        assertThatThrownBy(() -> service.forFoodId(CHICKEN_BREAST,
                new AlternativeQueryDto(10, new BigDecimal("100"), false, null, "NOPE")))
                .isInstanceOf(ReferenceNotFoundException.class);
    }

    @Test
    void leavesThePortionOutWhenNoneWasAskedAbout() {
        FoodAlternativesDto alternatives = service.forFoodId(CHICKEN_BREAST, 10, null, false);

        assertThat(alternatives.grams()).isNull();
        assertThat(alternatives.portion()).isNull();
        assertThat(alternatives.alternatives())
                .allSatisfy(alternative -> {
                    assertThat(alternative.equivalentGrams()).isNull();
                    assertThat(alternative.equivalentPortion()).isNull();
                });
    }

    @Test
    void findsTheFoodByTheNameADietWrites() {
        when(foods.entitiesByName(anyCollection()))
                .thenReturn(Map.of("lechuga", catalogue.get(6)));

        assertThat(service.forName("Lechuga", 10, null, false).foodId()).isEqualTo(LETTUCE);
    }

    @Test
    void refusesANameTheCatalogueDoesNotCarry() {
        when(foods.entitiesByName(anyCollection())).thenReturn(Map.of());

        assertThatThrownBy(() -> service.forName("pechuga de pollo", 10, null, false))
                .isInstanceOf(CompositionFoodNotFoundException.class)
                .hasMessageContaining("pechuga de pollo");
    }

    private static RationDto meatRation(FoodState state) {
        return new RationDto(1L, "AESAN-2022:CARNE", "AESAN-2022:ADULTOS", "Adultos",
                "AESAN-2022-007", "AESAN 2022", "CARNE", "Carnes", FoodCategory.MEAT, null, null,
                null, null, new BigDecimal("100"), new BigDecimal("125"), null, null, null, null,
                state, WeightBasis.NET_EDIBLE, null, null, "p. 52", null);
    }

    /** Energy as published: kilojoules, which is what 947 of the 957 rows use. */
    private static CompositionFood food(Long id, String name,
                                  String kj, String protein, String fat, String fibre) {
        CompositionFood food = blank(id, name);
        food.setEnergy(new NutrientValue(new BigDecimal(kj), "kJ"));
        food.setProtein(new NutrientValue(new BigDecimal(protein), "g"));
        food.setFat(new NutrientValue(new BigDecimal(fat), "g"));
        food.setFiber(new NutrientValue(new BigDecimal(fibre), "g"));
        return food;
    }

    private static CompositionFood blank(Long id, String name) {
        CompositionFood food = new CompositionFood();
        food.setId(id);
        food.setNameEs(name);
        food.setSource(CompositionSource.CIQUAL);
        return food;
    }
}
