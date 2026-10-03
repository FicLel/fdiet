package com.fdiet.diet.service;

import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.helpers.IMealTextParser;
import com.fdiet.diet.mapper.IDietMapper;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.repository.DietRepository;
import com.fdiet.diet.repository.PlannedDishRepository;
import com.fdiet.diet.repository.RecipeIngredientRepository;
import com.fdiet.diet.repository.RecipeRepository;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.patient.service.IPatientService;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting a diet. The schema cascades the week, the journal and the diet's own
 * measure criteria; what the service has to get right is the recipes — its
 * private ones go, after the plates have released them, and a library recipe it
 * served stays for every other plate that serves it. Nothing is archived or
 * reactivated on the way.
 */
class DietServiceDeleteTest {

    private static final Long DIET_ID = 14L;
    private static final Long OWN_RECIPE = 7L;
    private static final Long LIBRARY_RECIPE = 8L;

    private final DietRepository dietRepository = mock(DietRepository.class);
    private final PlannedDishRepository dishRepository = mock(PlannedDishRepository.class);
    private final RecipeRepository recipeRepository = mock(RecipeRepository.class);

    private final RecipeService recipeService = new RecipeService(
            recipeRepository,
            mock(RecipeIngredientRepository.class),
            mock(IDietMapper.class),
            mock(IMealTextParser.class),
            mock(IFoodResolverService.class),
            mock(IFoodItemService.class),
            mock(IBedcaFoodService.class),
            mock(IReferenceService.class),
            mock(IMeasureResolverService.class),
            5);

    private final DietService dietService = new DietService(
            dietRepository,
            dishRepository,
            mock(IDietMapper.class),
            recipeService,
            mock(IPatientService.class),
            mock(IReferenceService.class),
            mock(IDietRationService.class));

    @Test
    void deletesTheDietThenOnlyItsPrivateRecipes() {
        Recipe own = recipe(OWN_RECIPE, "Ensalada", false);
        Recipe shared = recipe(LIBRARY_RECIPE, "Huevos revueltos", true);
        List<Long> served = List.of(OWN_RECIPE, LIBRARY_RECIPE);
        when(dishRepository.recipeIdsOf(DIET_ID)).thenReturn(served);
        when(dietRepository.deleteWithWeekById(DIET_ID)).thenReturn(1);
        when(recipeRepository.findAllById(served)).thenReturn(List.of(own, shared));

        dietService.delete(DIET_ID);

        // The plates are read before the diet goes, and the recipes deleted after it.
        InOrder order = inOrder(dishRepository, dietRepository, recipeRepository);
        order.verify(dishRepository).recipeIdsOf(DIET_ID);
        order.verify(dietRepository).deleteWithWeekById(DIET_ID);
        order.verify(recipeRepository).deleteAll(List.of(own));
    }

    @Test
    void deletingTheActiveDietReactivatesNothing() {
        when(dishRepository.recipeIdsOf(DIET_ID)).thenReturn(List.of());
        when(dietRepository.deleteWithWeekById(DIET_ID)).thenReturn(1);

        dietService.delete(DIET_ID);

        verify(dietRepository, never()).save(any());
        verify(dietRepository, never()).saveAndFlush(any());
        verify(recipeRepository, never()).findAllById(anyCollection());
    }

    @Test
    void anUnknownDietIsNotFoundAndTouchesNoRecipe() {
        when(dishRepository.recipeIdsOf(DIET_ID)).thenReturn(List.of());
        when(dietRepository.deleteWithWeekById(DIET_ID)).thenReturn(0);

        assertThatThrownBy(() -> dietService.delete(DIET_ID))
                .isInstanceOf(DietNotFoundException.class);
        verify(recipeRepository, never()).deleteAll(anyCollection());
    }

    private static Recipe recipe(Long id, String name, boolean library) {
        Recipe recipe = new Recipe(name, null, null, library);
        recipe.setId(id);
        return recipe;
    }
}
