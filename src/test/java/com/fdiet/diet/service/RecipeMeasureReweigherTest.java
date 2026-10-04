package com.fdiet.diet.service;

import com.fdiet.diet.dto.DietProfileDto;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.MeasureScope;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.dto.ScopedMeasureQueryDto;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FD-054: a criterion written now reaches the ingredients the rule weighed, in
 * every week — each inside its own diet, a library recipe inside none — and only
 * those written in the criterion's measure.
 */
class RecipeMeasureReweigherTest {

    private static final long EGG = 2127L;
    private static final String EGG_NAME = "Huevo, entero, crudo";
    private static final long DIET = 5L;
    private static final long OTHER_DIET = 6L;
    private static final String PROFILE = "AESAN-2022:ADULTOS";

    private final IRecipeService recipeService = mock(IRecipeService.class);
    private final IDietService dietService = mock(IDietService.class);
    private final IReferenceService referenceService = mock(IReferenceService.class);
    private final RecipeMeasureReweigher reweigher =
            new RecipeMeasureReweigher(recipeService, dietService, referenceService);

    private final ReferenceFoodMeasure published = measure(30L);
    private final ReferenceFoodMeasure criterion = measure(40L);

    private final RecipeIngredient inDiet = eggs(recipe(1L, false), "unidades");
    private final RecipeIngredient inLibrary = eggs(recipe(2L, true), "unidad");
    private final RecipeIngredient inOtherDiet = eggs(recipe(3L, false), "unidades");
    private final RecipeIngredient inGrams = eggs(recipe(1L, false), "g");

    @Test
    void reweighsEveryDietAndTheLibraryForAGlobalCriterion() {
        stubStored();
        when(referenceService.rechoose(anyList())).thenReturn(List.of(criterion, criterion, published));

        int reweighed = reweigher.reweigh(new MeasureScope(EGG, HouseholdMeasure.UNIDAD, null));

        assertThat(reweighed).isEqualTo(2);
        assertThat(inDiet.getFoodMeasure()).isSameAs(criterion);
        assertThat(inLibrary.getFoodMeasure()).isSameAs(criterion);
        assertThat(inOtherDiet.getFoodMeasure()).isSameAs(published);
        assertThat(inDiet.isMeasurePicked()).isFalse();
        verify(referenceService).rechoose(List.of(
                scoped("unidades", DIET, PROFILE),
                scoped("unidad", null, null),
                scoped("unidades", OTHER_DIET, null)));
        verify(recipeService).saveIngredients(List.of(inDiet, inLibrary));
    }

    /** One diet's criterion is not everybody's, and no library recipe is weighed by it. */
    @Test
    void reweighsOnlyThatDietsPrivateRecipesForADietsCriterion() {
        stubStored();
        when(referenceService.rechoose(anyList())).thenReturn(List.of(criterion));

        int reweighed = reweigher.reweigh(new MeasureScope(EGG, HouseholdMeasure.UNIDAD, DIET));

        assertThat(reweighed).isEqualTo(1);
        assertThat(inLibrary.getFoodMeasure()).isSameAs(published);
        verify(referenceService).rechoose(List.of(scoped("unidades", DIET, PROFILE)));
    }

    @Test
    void asksNothingWhenNoIngredientIsWrittenInTheMeasure() {
        when(recipeService.autoMeasured(EGG)).thenReturn(List.of(inGrams));

        assertThat(reweigher.reweigh(new MeasureScope(EGG, HouseholdMeasure.UNIDAD, null))).isZero();
        verify(referenceService, never()).rechoose(anyList());
    }

    private void stubStored() {
        when(recipeService.autoMeasured(EGG)).thenReturn(List.of(inDiet, inLibrary, inOtherDiet, inGrams));
        when(dietService.dietsServingPrivate(List.of(1L, 3L))).thenReturn(Map.of(
                1L, new DietProfileDto(1L, DIET, PROFILE),
                3L, new DietProfileDto(3L, OTHER_DIET, null)));
    }

    private static ScopedMeasureQueryDto scoped(String unit, Long dietId, String profile) {
        return new ScopedMeasureQueryDto(new MeasureQueryDto(EGG, EGG_NAME, unit, null, null),
                dietId, profile);
    }

    private RecipeIngredient eggs(Recipe recipe, String unit) {
        CompositionFood egg = new CompositionFood();
        egg.setId(EGG);
        egg.setNameEs(EGG_NAME);
        RecipeIngredient ingredient = new RecipeIngredient("huevo", null, egg, new BigDecimal("2"), unit);
        ingredient.setRecipe(recipe);
        ingredient.setFoodMeasure(published);
        return ingredient;
    }

    private static Recipe recipe(Long id, boolean library) {
        Recipe recipe = new Recipe("Tortilla", null, null, library);
        recipe.setId(id);
        return recipe;
    }

    private static ReferenceFoodMeasure measure(Long id) {
        ReferenceFoodMeasure row = new ReferenceFoodMeasure();
        row.setId(id);
        return row;
    }
}
