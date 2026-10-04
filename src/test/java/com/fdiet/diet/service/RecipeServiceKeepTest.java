package com.fdiet.diet.service;

import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.KeptMatchDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.helpers.MealTextParser;
import com.fdiet.diet.helpers.PortionScaler;
import com.fdiet.diet.mapper.DietMapper;
import com.fdiet.diet.repository.RecipeIngredientRepository;
import com.fdiet.diet.repository.RecipeRepository;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FD-048 through {@code POST /api/diets/parse}: the editor reads a cell again
 * after every edit, and a match a person made survives it while the ingredient
 * keeps its name.
 */
class RecipeServiceKeepTest {

    private static final long LETTUCE = 7L;
    private static final long PREFERRED_EGG = 2127L;
    private static final long OTHER_EGG = 2130L;
    private static final long NO_SPANISH_NAME = 9001L;
    private static final long EGG_MEASURE = 40L;

    private final RecipeRepository recipes = mock(RecipeRepository.class);
    private final IReferenceService reference = mock(IReferenceService.class);
    private final IFoodResolverService resolver = mock(IFoodResolverService.class);
    private final ICompositionFoodService compositionFoods = mock(ICompositionFoodService.class);
    private final IFoodItemService foodItems = mock(IFoodItemService.class);

    private final RecipeService service = new RecipeService(
            recipes,
            mock(RecipeIngredientRepository.class),
            new DietMapper(mock(IDietNutritionService.class), reference),
            new MealTextParser(),
            new IngredientFoodService(compositionFoods, foodItems, resolver, 5),
            new MeasureResolverService(reference, new PortionScaler()));

    /** Most CIQUAL / BLS foods have no Spanish name, so the name alone would match nothing. */
    @Test
    void keepsAFoodWithoutASpanishName() {
        CompositionFood quark = food(NO_SPANISH_NAME, null);
        quark.setNameEn("Quark, plain");
        when(compositionFoods.entitiesByIds(anyCollection())).thenReturn(Map.of(NO_SPANISH_NAME, quark));

        RecipeDto read = read("Quark, plain (100 g)",
                new KeptMatchDto("Quark, plain", NO_SPANISH_NAME, null, null));

        DishIngredient kept = read.ingredients().get(0);
        assertThat(kept.compositionFoodId()).isEqualTo(NO_SPANISH_NAME);
        assertThat(kept.matchedName()).isEqualTo(quark.label());
    }

    /** The person chose another row than the one the name's preferred flag would give. */
    @Test
    void keepsANonPreferredFoodOverThePreferredOne() {
        when(resolver.resolve(anyCollection())).thenReturn(
                Map.of("huevo", FoodMatch.of(food(PREFERRED_EGG, "Huevo, entero, crudo"))));
        when(compositionFoods.entitiesByIds(anyCollection()))
                .thenReturn(Map.of(OTHER_EGG, food(OTHER_EGG, "Huevo, entero, cocido")));

        RecipeDto read = read("Huevo (60 g)", new KeptMatchDto("huevo", OTHER_EGG, null, null));

        assertThat(read.ingredients().get(0).compositionFoodId()).isEqualTo(OTHER_EGG);
        // The kept food goes in by id: its name is never asked of the resolver.
        verify(resolver).resolve(argThat(Collection::isEmpty));
    }

    /** An edited name is a new ingredient: parse decides, and the other ingredient keeps its match. */
    @Test
    void anEditedNameIsMatchedAgainAndTheOthersKeepTheirs() {
        when(resolver.resolve(anyCollection())).thenReturn(Map.of());
        when(compositionFoods.entitiesByIds(anyCollection()))
                .thenReturn(Map.of(OTHER_EGG, food(OTHER_EGG, "Huevo, entero, cocido")));

        RecipeDto read = read("Ensalada: lechuga romana (80 g) + huevo (60 g)",
                new KeptMatchDto("lechuga", LETTUCE, null, null),
                new KeptMatchDto("huevo", OTHER_EGG, null, null));

        assertThat(read.ingredients()).extracting(DishIngredient::compositionFoodId)
                .containsExactly(null, OTHER_EGG);
        verify(resolver).resolve(argThat(names -> names.size() == 1 && names.contains("lechuga romana")));
    }

    /** A household measure was picked; the quantity is now written in grams, which weigh themselves. */
    @Test
    void dropsTheKeptMeasureWhenTheUnitChanges() {
        when(compositionFoods.entitiesByIds(anyCollection()))
                .thenReturn(Map.of(PREFERRED_EGG, food(PREFERRED_EGG, "Huevo, entero, crudo")));

        RecipeDto read = read("huevo (60 g)",
                new KeptMatchDto("huevo", PREFERRED_EGG, null, EGG_MEASURE));

        DishIngredient kept = read.ingredients().get(0);
        assertThat(kept.compositionFoodId()).isEqualTo(PREFERRED_EGG);
        assertThat(kept.foodMeasureId()).isNull();
        assertThat(kept.measurePicked()).isFalse();
        verify(reference, never()).chooseMeasures(anyList(), isNull(), isNull());
    }

    /** Unit unchanged: the kept measure is handed on as the pick, re-validated as FD-039 does. */
    @Test
    void offersTheKeptMeasureAsThePickWhileTheUnitIsUnchanged() {
        when(compositionFoods.entitiesByIds(anyCollection()))
                .thenReturn(Map.of(PREFERRED_EGG, food(PREFERRED_EGG, "Huevo, entero, crudo")));
        when(reference.chooseMeasures(anyList(), isNull(), isNull()))
                .thenReturn(List.of(MeasureChoiceDto.NONE));

        read("huevo (2 unidades)", new KeptMatchDto("huevo", PREFERRED_EGG, null, EGG_MEASURE));

        verify(reference).chooseMeasures(argThat(queries -> queries.size() == 1
                && Long.valueOf(EGG_MEASURE).equals(queries.get(0).preferredMeasureId())), isNull(), isNull());
    }

    @Test
    void refusesAKeptMatchNamingBothFoods() {
        assertThatThrownBy(() -> read("huevo (60 g)", new KeptMatchDto("huevo", PREFERRED_EGG, 31L, null)))
                .isInstanceOf(InvalidDietException.class)
                .hasMessageContaining("foodItemId");
    }

    private RecipeDto read(String text, KeptMatchDto... keep) {
        return service.read(text, "Primer plato", null, null, null, null, List.of(keep));
    }

    private static CompositionFood food(long id, String nameEs) {
        CompositionFood food = new CompositionFood();
        food.setId(id);
        food.setNameEs(nameEs);
        return food;
    }
}
