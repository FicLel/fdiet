package com.fdiet.diet.service;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.diet.dto.DietRationsDto;
import com.fdiet.diet.dto.DietRationsDto.DayRations;
import com.fdiet.diet.dto.DietRationsDto.Status;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.helpers.PortionScaler;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.model.Recipe;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.NutrientValue;
import com.fdiet.food.service.NutritionService;
import com.fdiet.reference.domain.ExchangeNutrient;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.RecommendationPeriod;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.MealShareDto;
import com.fdiet.reference.dto.MealSharesDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.RecommendationDto;
import com.fdiet.reference.dto.ReferenceProfileDto;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A week in rations: ranges divide into ranges, a ceiling is a ceiling, and
 * nothing is counted in a state the ration is not defined in.
 */
class DietRationServiceTest {

    private static final String PROFILE = "AESAN-2022:ADULTOS";

    private static final RationDto LEGUMES = ration("LEGUMBRES", "Legumbres", FoodCategory.LEGUME,
            "50", "60", FoodState.DRY);
    private static final RationDto FRUIT = ration("FRUTAS", "Frutas", FoodCategory.FRUIT,
            "120", "200", FoodState.UNSPECIFIED);

    private final IReferenceService reference = mock(IReferenceService.class);
    private final DietRationService service = new DietRationService(
            new DietNutritionService(new NutritionService(), new PortionScaler()), reference);

    @BeforeEach
    void profile() {
        when(reference.profileSummary(PROFILE)).thenReturn(new ReferenceProfileDto(PROFILE,
                "Población española adulta", "AESAN-2022-007", "AESAN 2022", 216, null, null, true, false));
        when(reference.recommendations(PROFILE)).thenReturn(List.of(
                new RecommendationDto("R-LEG", "Legumbres", List.of("LEGUMBRES"), new BigDecimal("4"),
                        null, RecommendationPeriod.PER_WEEK, "p. 51", null),
                new RecommendationDto("R-FRUTAS", "Frutas", List.of("FRUTAS"), new BigDecimal("2"),
                        new BigDecimal("3"), RecommendationPeriod.PER_DAY, "p. 50", null)));
        when(reference.mealShares(PROFILE)).thenReturn(new MealSharesDto("ESCOLAR", "Alumnado",
                "AESAN-MEC-2010", "AESAN/MEC 2010", true, "Tomado del documento escolar", "p. 15",
                List.of(new MealShareDto("LUNCH", new BigDecimal("35"), new BigDecimal("35"), null))));
        when(reference.exchangeSystems(false)).thenReturn(List.of());
        when(reference.exchangeSystems(true)).thenReturn(List.of(new ExchangeSystemDto("RACION-HC-10",
                "Ración de hidratos de carbono", ExchangeNutrient.CARBOHYDRATE, BigDecimal.TEN, true,
                "FUNDACION-DIABETES-HC", "Raciones de HC", null)));
        when(reference.sources()).thenReturn(List.of());
        when(reference.countingRation(eq(PROFILE), any(), anyString())).thenAnswer(call -> {
            String name = call.getArgument(2);
            return name.startsWith("Lenteja") ? LEGUMES : name.startsWith("Manzana") ? FRUIT : null;
        });
    }

    @Test
    void dividesARangeRationIntoARangeCount() {
        DayRations monday = monday(plan(false,
                ingredient("lentejas", "60", "g", FoodState.RAW, food(1L, "Lenteja, seca, cruda", "20")),
                ingredient("manzana", "160", "g", null, food(2L, "Manzana", "12"))));

        assertThat(monday.groups()).extracting(DietRationsDto.GroupCount::groupCode)
                .containsExactly("LEGUMBRES", "FRUTAS");
        // 60 g against 50-60 g is 1 to 1,2 rations; 160 g against 120-200 g is 0,8 to 1,33.
        assertThat(monday.groups().get(0).rationsMin()).isEqualByComparingTo("1.00");
        assertThat(monday.groups().get(0).rationsMax()).isEqualByComparingTo("1.20");
        assertThat(monday.groups().get(1).rationsMin()).isEqualByComparingTo("0.80");
        assertThat(monday.groups().get(1).rationsMax()).isEqualByComparingTo("1.33");
    }

    /** Two servings of a shared recipe are twice the rations of one. */
    @Test
    void countsTheRationsOfEveryServing() {
        DayRations monday = monday(plan(false, new BigDecimal("2"),
                ingredient("lentejas", "60", "g", FoodState.RAW, food(1L, "Lenteja, seca, cruda", "20"))));

        assertThat(monday.groups()).singleElement().satisfies(legumes -> {
            assertThat(legumes.rationsMin()).isEqualByComparingTo("2.00");
            assertThat(legumes.rationsMax()).isEqualByComparingTo("2.40");
        });
        assertThat(monday.coverage().ingredients()).isEqualTo(1);
    }

    @Test
    void checksRecommendationsRangeAgainstRange() {
        DietRationsDto week = service.account(plan(false,
                ingredient("lentejas", "60", "g", FoodState.RAW, food(1L, "Lenteja, seca, cruda", "20")),
                ingredient("manzana", "160", "g", null, food(2L, "Manzana", "12"))), null);

        assertThat(week.weekly()).singleElement().satisfies(legumes -> {
            assertThat(legumes.actualMax()).isEqualByComparingTo("1.20");
            assertThat(legumes.status()).isEqualTo(Status.BELOW);
        });
        assertThat(week.days().get(0).daily()).singleElement()
                .extracting(DietRationsDto.RecommendationCheck::status).isEqualTo(Status.BELOW);
    }

    @Test
    void anUncountedIngredientTurnsAShortfallIntoUncertainty() {
        DietRationsDto week = service.account(plan(false,
                ingredient("lentejas", "60", "g", FoodState.RAW, food(1L, "Lenteja, seca, cruda", "20")),
                ingredient("manzana", "160", "g", null, food(2L, "Manzana", "12")),
                ingredient("garbanzos", "60", "g", null, null)), null);

        // The unmatched chickpeas may be the rest of the legumes, or more fruit.
        assertThat(week.weekly()).singleElement()
                .extracting(DietRationsDto.RecommendationCheck::status).isEqualTo(Status.UNCERTAIN);
        assertThat(week.days().get(0).daily()).singleElement()
                .extracting(DietRationsDto.RecommendationCheck::status).isEqualTo(Status.UNCERTAIN);
    }

    @Test
    void namesEveryIngredientItCouldNotCountAndWhy() {
        DayRations monday = monday(plan(false,
                ingredient("lentejas cocidas", "180", "g", FoodState.COOKED, food(3L, "Lenteja, hervida", "16")),
                ingredient("pan", "40", "g", null, null),
                ingredient("lechuga", "1", "unidad", null, food(4L, "Lechuga", "1")),
                ingredient("aceite", "10", "ml", null, food(5L, "Aceite de oliva", "0")),
                ingredient("manzana", "160", "g", null, food(2L, "Manzana", "12"))));

        DietRationsDto.Coverage coverage = monday.coverage();
        assertThat(coverage.ingredients()).isEqualTo(5);
        assertThat(coverage.counted()).isEqualTo(1);
        assertThat(coverage.stateMismatch()).isEqualTo(1);
        assertThat(coverage.unmatched()).isEqualTo(1);
        assertThat(coverage.unweighed()).isEqualTo(1);
        assertThat(coverage.noRation()).isEqualTo(1);
        assertThat(coverage.counted() + coverage.stateMismatch() + coverage.unmatched()
                + coverage.unweighed() + coverage.noRation()).isEqualTo(coverage.ingredients());
        assertThat(monday.uncounted()).extracting(DietRationsDto.Uncounted::reason)
                .contains("Pesado cocinado y la ración es en seco");
    }

    @Test
    void drawsTheMealShareBesideTheBorrowedTarget() {
        DietRationsDto week = service.account(plan(false,
                ingredient("manzana", "160", "g", null, food(2L, "Manzana", "12"))), null);

        assertThat(week.mealShares().borrowed()).isTrue();
        assertThat(week.days().get(0).meals()).singleElement().satisfies(lunch -> {
            assertThat(lunch.pct()).isEqualByComparingTo("100.0");
            assertThat(lunch.targetPctMin()).isEqualByComparingTo("35");
        });
    }

    @Test
    void countsCarbohydrateRationsOnlyOnAClinicalDiet() {
        RecipeIngredient apple = ingredient("manzana", "160", "g", null, food(2L, "Manzana", "12"));

        assertThat(service.account(plan(false, apple), null).days().get(0).exchanges()).isEmpty();

        DayRations clinical = monday(plan(true,
                ingredient("manzana", "160", "g", null, food(2L, "Manzana", "12"))));
        // 12 g of carbohydrate per 100 g, 160 g of it: 19,2 g, 1,9 rations of 10 g.
        assertThat(clinical.exchanges()).singleElement().satisfies(hc -> {
            assertThat(hc.dayUnits()).isEqualByComparingTo("1.9");
            assertThat(hc.meals()).singleElement()
                    .extracting(DietRationsDto.MealUnits::units).isEqualTo(new BigDecimal("1.9"));
            assertThat(hc.dishes()).singleElement().satisfies(dish -> {
                assertThat(dish.dishIndex()).isZero();
                assertThat(dish.units()).isEqualByComparingTo("1.9");
                assertThat(dish.complete()).isTrue();
            });
        });
    }

    @Test
    void countsNothingWithoutAProfile() {
        DietPlan plan = plan(false, ingredient("manzana", "160", "g", null, food(2L, "Manzana", "12")));
        plan.setReferenceProfileCode(null);
        when(reference.countingRation(any(), any(), anyString())).thenReturn(null);

        DietRationsDto week = service.account(plan, null);

        assertThat(week.profile()).isNull();
        assertThat(week.days().get(0).groups()).isEmpty();
        assertThat(week.days().get(0).meals()).hasSize(1);
    }

    private DayRations monday(DietPlan plan) {
        return service.account(plan, null).days().get(0);
    }

    private static DietPlan plan(boolean clinical, RecipeIngredient... ingredients) {
        return plan(clinical, BigDecimal.ONE, ingredients);
    }

    private static DietPlan plan(boolean clinical, BigDecimal servings,
                                 RecipeIngredient... ingredients) {
        DietPlan plan = new DietPlan();
        plan.setId(1L);
        plan.setReferenceProfileCode(PROFILE);
        plan.setClinical(clinical);
        PlannedMeal lunch = new PlannedMeal(DayOfWeek.MONDAY, MealType.LUNCH, "Comida");
        plan.addMeal(lunch);
        Recipe recipe = new Recipe("Plato", null, null, false);
        for (RecipeIngredient ingredient : ingredients) {
            recipe.addIngredient(ingredient);
        }
        lunch.addDish(new PlannedDish("Plato", recipe, servings));
        return plan;
    }

    private static RecipeIngredient ingredient(String name, String quantity, String unit,
                                                FoodState state, CompositionFood food) {
        RecipeIngredient ingredient =
                new RecipeIngredient(name, null, food, new BigDecimal(quantity), unit);
        ingredient.setState(state);
        return ingredient;
    }

    private static CompositionFood food(Long id, String name, String carbohydrates) {
        CompositionFood food = new CompositionFood();
        food.setId(id);
        food.setNameEs(name);
        food.setEnergy(new NutrientValue(new BigDecimal("200"), "kJ"));
        food.setCarbohydrates(new NutrientValue(new BigDecimal(carbohydrates), "g"));
        return food;
    }

    private static RationDto ration(String group, String label, FoodCategory category, String min,
                                    String max, FoodState state) {
        return new RationDto(1L, "AESAN-2022:" + group, PROFILE, "Población española adulta",
                "AESAN-2022-007", "AESAN 2022", group, label, category, null, null, null, null,
                new BigDecimal(min), new BigDecimal(max), null, null, null, null, state,
                WeightBasis.UNSPECIFIED, null, null, "p. 52", null);
    }
}
