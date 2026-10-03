package com.fdiet.diet.service;

import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.helpers.MealTextParser;
import com.fdiet.diet.helpers.PortionScaler;
import com.fdiet.diet.mapper.DietMapper;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.repository.RecipeIngredientRepository;
import com.fdiet.diet.repository.RecipeRepository;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.mapper.ReferenceMapper;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The library's rules: a name finds one recipe, a shared recipe is weighed by
 * nothing one diet owns, a recipe on a plate stays on it, and a plate points only
 * at the library or at its own diet's recipes.
 */
class RecipeServiceTest {

    private final RecipeRepository recipes = mock(RecipeRepository.class);
    private final IReferenceService reference = mock(IReferenceService.class);
    private final IFoodResolverService resolver = mock(IFoodResolverService.class);
    private final IBedcaFoodService bedcaFoods = mock(IBedcaFoodService.class);

    private final RecipeService service = new RecipeService(
            recipes,
            mock(RecipeIngredientRepository.class),
            new DietMapper(mock(IDietNutritionService.class), reference),
            new MealTextParser(),
            resolver,
            mock(IFoodItemService.class),
            bedcaFoods,
            reference,
            new MeasureResolverService(reference, new PortionScaler()),
            5);

    @Test
    void refusesALibraryNameAnotherRecipeHolds() {
        Recipe holder = recipe(3L, "Huevos revueltos", true);
        when(recipes.findFirstByLibraryTrueAndName("huevos revueltos")).thenReturn(Optional.of(holder));

        assertThatThrownBy(() -> service.create(content("huevos revueltos", List.of())))
                .isInstanceOf(InvalidDietException.class)
                .hasMessageContaining("Huevos revueltos");
        verify(recipes, never()).saveAndFlush(any());
    }

    @Test
    void refusesALibraryRecipeWithNoName() {
        assertThatThrownBy(() -> service.create(content("  ", List.of())))
                .isInstanceOf(InvalidDietException.class);
    }

    /** One diet's criterion goes with that diet, and a shared recipe would go unweighed everywhere. */
    @Test
    void refusesToWeighALibraryRecipeByOneDietsOwnMeasure() {
        ReferenceFoodMeasure own = new ReferenceFoodMeasure();
        own.setId(11L);
        own.setDietId(5L);
        when(recipes.findFirstByLibraryTrueAndName(any())).thenReturn(Optional.empty());
        when(reference.measureEntities(anyCollection())).thenReturn(Map.of(11L, own));
        DishIngredient oil = new DishIngredient(null, "AOVE", BigDecimal.ONE, null, "cdta", null, null,
                null, null, 11L, null, null, false, null, null, null);

        assertThatThrownBy(() -> service.create(content("Aliño", List.of(oil))))
                .isInstanceOf(InvalidDietException.class)
                .hasMessageContaining("[11]");
        verify(recipes, never()).saveAndFlush(any());
    }

    /** The nutritionist's global criterion belongs to no diet, so a shared recipe may be weighed by it. */
    @Test
    void weighsALibraryRecipeByTheNutritionistsGlobalCriterion() {
        ReferenceFoodMeasure global = new ReferenceFoodMeasure();
        global.setId(40L);
        global.setGlobalCriterion(true);
        BedcaFood egg = new BedcaFood();
        egg.setId(2127L);
        egg.setName("Huevo, entero, crudo");
        FoodMeasureDto chosen = new ReferenceMapper().toDto(globalEgg(global));
        when(recipes.findFirstByLibraryTrueAndName(any())).thenReturn(Optional.empty());
        when(recipes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(reference.measureEntities(anyCollection())).thenReturn(Map.of(40L, global));
        when(bedcaFoods.entitiesByIds(anyCollection())).thenReturn(Map.of(2127L, egg));
        when(reference.chooseMeasures(anyList(), isNull(), isNull()))
                .thenReturn(List.of(new MeasureChoiceDto(chosen, List.of(chosen))));
        DishIngredient eggs = new DishIngredient(null, "Huevo", new BigDecimal("2"), null, "unidades", null,
                PortionSize.MEDIUM, null, 2127L, 40L, null, null, false, null, null, null);

        service.create(content("Huevos revueltos", List.of(eggs)));

        verify(recipes).saveAndFlush(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getIngredients().size() == 1
                        && saved.getIngredients().get(0).getFoodMeasure() == global));
    }

    @Test
    void readsTheTextWhenNoIngredientsAreSent() {
        when(recipes.findFirstByLibraryTrueAndName(any())).thenReturn(Optional.empty());
        when(resolver.resolve(anyCollection())).thenReturn(Map.of());
        when(recipes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.create(new RecipeDto(null, "Tortilla", null, "Batir y cuajar.",
                "2 huevos + 5 ml AOVE", List.of(), null));

        verify(recipes).saveAndFlush(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.isLibrary()
                        && saved.getIngredients().size() == 2
                        && saved.getSteps().equals("Batir y cuajar.")
                        && saved.getRawText().equals("2 huevos + 5 ml AOVE")));
    }

    @Test
    void refusesToDeleteARecipeStillOnAPlate() {
        Recipe served = recipe(3L, "Huevos revueltos", true);
        when(recipes.findById(3L)).thenReturn(Optional.of(served));
        doThrow(new DataIntegrityViolationException("fk_diet_dishes_recipe")).when(recipes).flush();

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(InvalidDietException.class)
                .hasMessageContaining("/api/recipes/3/usage");
    }

    @Test
    void rewritesOnlyLibraryRecipesThroughTheLibrary() {
        when(recipes.findById(4L)).thenReturn(Optional.of(recipe(4L, "Ensalada", false)));

        assertThatThrownBy(() -> service.update(4L, content("Ensalada", List.of())))
                .isInstanceOf(InvalidDietException.class);
    }

    @Test
    void linksALibraryRecipeOrOneOfTheDietsOwn() {
        when(recipes.findAllById(any())).thenReturn(List.of(
                recipe(1L, "Huevos revueltos", true), recipe(2L, "Ensalada", false)));

        Map<Long, Recipe> linked = service.linkable(List.of(1L, 2L), Set.of(2L));

        assertThat(linked).containsOnlyKeys(1L, 2L);
    }

    /** A private recipe of another diet is that diet's plate, not this one's. */
    @Test
    void refusesAnotherDietsPrivateRecipe() {
        when(recipes.findAllById(any())).thenReturn(List.of(recipe(2L, "Ensalada", false)));

        assertThatThrownBy(() -> service.linkable(List.of(2L, 9L), Set.of()))
                .isInstanceOf(InvalidDietException.class)
                .hasMessageContaining("[2, 9]");
    }

    @Test
    void deletesOnlyThePrivateRecipesAmongThoseAsked() {
        Recipe library = recipe(1L, "Huevos revueltos", true);
        Recipe own = recipe(2L, "Ensalada", false);
        when(recipes.findAllById(any())).thenReturn(List.of(library, own));

        service.deletePrivate(List.of(1L, 2L));

        verify(recipes).deleteAll(List.of(own));
    }

    private static ReferenceFoodMeasure globalEgg(ReferenceFoodMeasure row) {
        row.setMeasure(HouseholdMeasure.UNIDAD);
        row.setSize(PortionSize.MEDIUM);
        row.setCount(BigDecimal.ONE);
        row.setCompositionFoodId(2127L);
        row.setFoodLabel("Huevo, entero, crudo");
        row.setGramsMin(new BigDecimal("58"));
        row.setGramsMax(new BigDecimal("58"));
        row.setState(FoodState.UNSPECIFIED);
        row.setWeightBasis(WeightBasis.NET_EDIBLE);
        return row;
    }

    private static RecipeDto content(String name, List<DishIngredient> ingredients) {
        return new RecipeDto(null, name, null, null, null, ingredients, null);
    }

    private static Recipe recipe(Long id, String name, boolean library) {
        Recipe recipe = new Recipe(name, null, null, library);
        recipe.setId(id);
        return recipe;
    }
}
