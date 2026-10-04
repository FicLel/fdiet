package com.fdiet.diet.service;

import com.fdiet.diet.domain.Serving;
import com.fdiet.diet.dto.ComposeRequestDto;
import com.fdiet.diet.dto.ComposedFragmentDto;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.helpers.MealTextParser;
import com.fdiet.diet.helpers.PortionScaler;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.NutrientValue;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.NutritionService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FD-060: the text the composer writes for a cooked ration reads back cooked
 * through the real parser, so a cooked weight composed onto a dry food is
 * flagged and left out of the totals instead of being priced as dry lentils.
 */
class ComposedStateRoundTripTest {

    private static final long DRY_LENTILS = 20501L;

    private final MealTextParser parser = new MealTextParser();
    private final IRecipeService recipeService = mock(IRecipeService.class);
    private final ICompositionFoodService compositionFoodService = mock(ICompositionFoodService.class);
    private final DietComposeService composeService = new DietComposeService(
            mock(IDietService.class),
            recipeService,
            compositionFoodService,
            mock(IReferenceService.class),
            mock(IMeasureResolverService.class));
    private final DietNutritionService nutrition =
            new DietNutritionService(new NutritionService(), new PortionScaler());

    @Test
    void aCookedRationComposedOntoADryFoodReadsBackCookedAndIsNotTotalled() {
        CompositionFood lentils = dryLentils();
        when(compositionFoodService.entityById(DRY_LENTILS)).thenReturn(lentils);
        // The composer reads its fragment back through the recipe service; here that is the real parser.
        when(recipeService.read(anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> parser.parse(call.getArgument(0), call.getArgument(1)));

        ComposedFragmentDto composed = composeService.compose(new ComposeRequestDto(
                DRY_LENTILS, new BigDecimal("150"), null, null, FoodState.COOKED, null, null));

        assertThat(composed.fragment()).isEqualTo("Lenteja, seca, cruda (150 g cocinado)");
        DishIngredient read = composed.ingredient();
        assertThat(read.state()).isEqualTo(FoodState.COOKED);
        assertThat(read.quantity()).isEqualByComparingTo("150");

        RecipeIngredient stored = new RecipeIngredient(read.name(), null, lentils, read.quantity(), read.unit());
        stored.setState(read.state());
        assertThat(stored.isStateMismatch()).isTrue();

        NutritionSummaryDto summary = nutrition.summarise(Serving.single(List.of(stored)));
        assertThat(nutrition.of(stored)).isNull();
        assertThat(summary.counted()).isZero();
        assertThat(summary.unmeasured()).isEqualTo(1);
        assertThat(summary.unmeasuredByState()).isEqualTo(1);
        assertThat(summary.totals().isEmpty()).isTrue();
    }

    private static CompositionFood dryLentils() {
        CompositionFood food = new CompositionFood();
        food.setId(DRY_LENTILS);
        food.setNameEs("Lenteja, seca, cruda");
        food.setEnergy(new NutrientValue(new BigDecimal("1340"), "kJ"));
        food.setProtein(new NutrientValue(new BigDecimal("24"), "g"));
        return food;
    }
}
