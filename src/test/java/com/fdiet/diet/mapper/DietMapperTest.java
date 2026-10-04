package com.fdiet.diet.mapper;

import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.dto.YieldFactorDto;
import com.fdiet.diet.service.IDietNutritionService;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A plate crosses out as its description, its servings and the recipe behind it,
 * with the recipe's written text intact.
 *
 * <p>The text has to survive: an editor puts it back in front of the
 * nutritionist, and a sentence rebuilt from the parts is not the one written.
 */
class DietMapperTest {

    private static final String CELL =
            "Tostada de pan integral (60 gr) con tomate rallado (80 gr)";

    private final IDietNutritionService nutrition = mock(IDietNutritionService.class);
    private final IReferenceService reference = mock(IReferenceService.class);
    private final DietMapper mapper = new DietMapper(nutrition, reference);

    @Test
    void carriesTheDescriptionServingsAndRecipeTextOut() {
        Recipe recipe = new Recipe("Tostada con tomate", CELL, "Tostar el pan y rallar el tomate.", false);
        PlannedDish entity = new PlannedDish("Tostada", recipe, new BigDecimal("1.5"));

        Dish dish = mapper.toDto(entity);

        assertThat(dish.name()).isEqualTo("Tostada");
        assertThat(dish.servings()).isEqualByComparingTo("1.5");
        assertThat(dish.recipe().rawText()).isEqualTo(CELL);
        assertThat(dish.recipe().steps()).isEqualTo("Tostar el pan y rallar el tomate.");
        assertThat(dish.recipe().library()).isFalse();
    }

    /** A plate that is a description only has no recipe, and none is invented for it. */
    @Test
    void leavesTheRecipeNullOnADescriptionOnlyPlate() {
        Dish dish = mapper.toDto(new PlannedDish("Comida libre"));

        assertThat(dish.recipe()).isNull();
        assertThat(dish.recipeId()).isNull();
        assertThat(dish.servings()).isEqualByComparingTo("1");
    }

    /** 150 g of raw breast priced against grilled breast: 108 g cooked, by USDA's 72 %, offered. */
    @Test
    void offersAYieldWhenTheTextAndTheFoodDisagreeAboutCooking() {
        CompositionFood grilled = new CompositionFood();
        grilled.setId(2297L);
        grilled.setNameEs("Pollo, pechuga, plancha");
        RecipeIngredient raw = new RecipeIngredient("pechuga de pollo", null, grilled,
                new BigDecimal("150"), "g");
        raw.setState(FoodState.RAW);
        when(nutrition.edibleGrams(raw)).thenReturn(new BigDecimal("150"));
        when(reference.yieldFactors(eq("Pollo, pechuga, plancha"), eq("Pollo, pechuga, plancha")))
                .thenReturn(List.of(new YieldFactorDto(1L, "USDA-Y:POLLO-PECHUGA-ASADA",
                        "USDA-YIELDS-2014", "USDA 2014", null, "pollo pechuga",
                        "Chicken, broiler-fryer, breast, meat and skin",
                        "Baked or Roasted, unspecified", "asado;asada", new BigDecimal("72"), null,
                        "Tabla 1: NDB 5060", null)));

        DishIngredient read = mapper.toDto(raw);

        assertThat(read.stateMismatch()).isTrue();
        assertThat(read.yieldHint()).satisfies(hint -> {
            assertThat(hint.writtenState()).isEqualTo(FoodState.RAW);
            assertThat(hint.foodState()).isEqualTo(FoodState.COOKED);
            assertThat(hint.equivalentGrams()).isEqualByComparingTo("108");
            assertThat(hint.methodNamed()).isFalse();
        });
        // Offered, never applied: the quantity stays what was written.
        assertThat(read.quantity()).isEqualByComparingTo("150");
    }

    @Test
    void asksForNoYieldWhenNothingDisagrees() {
        CompositionFood grilled = new CompositionFood();
        grilled.setId(2297L);
        grilled.setNameEs("Pollo, pechuga, plancha");
        RecipeIngredient plain = new RecipeIngredient("pechuga de pollo", null, grilled,
                new BigDecimal("120"), "g");

        assertThat(mapper.toDto(plain).yieldHint()).isNull();
        verify(reference, never()).yieldFactors(any(), any());
    }
}
