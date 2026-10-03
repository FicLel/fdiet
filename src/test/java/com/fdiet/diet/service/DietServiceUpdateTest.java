package com.fdiet.diet.service;

import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.mapper.DietMapper;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.DietStatus;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.repository.DietRepository;
import com.fdiet.diet.repository.PlannedDishRepository;
import com.fdiet.patient.model.Patient;
import com.fdiet.patient.service.IPatientService;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Replacing a week, as far as its recipes go: the plates keep the recipes they
 * name, a plate's own recipe is written afresh, and the private recipes nothing
 * serves any more go with the week they belonged to. Library recipes stay.
 */
class DietServiceUpdateTest {

    private static final Long DIET_ID = 1L;
    private static final Long PATIENT_ID = 3L;

    private final DietRepository dietRepository = mock(DietRepository.class);
    private final IRecipeService recipeService = mock(IRecipeService.class);

    private final DietService dietService = new DietService(
            dietRepository,
            mock(PlannedDishRepository.class),
            new DietMapper(mock(IDietNutritionService.class), mock(IReferenceService.class)),
            recipeService,
            mock(IPatientService.class),
            mock(IReferenceService.class),
            mock(IDietRationService.class));

    private final Recipe ownSalad = recipe(7L, "Ensalada", false);
    private final Recipe sharedEggs = recipe(8L, "Huevos revueltos", true);
    private DietPlan plan;

    @BeforeEach
    void storedWeek() {
        plan = new DietPlan();
        plan.setId(DIET_ID);
        Patient patient = new Patient("Ana", null);
        patient.setId(PATIENT_ID);
        plan.setPatient(patient);
        plan.setName("Semana");
        plan.setStatus(DietStatus.ACTIVE);
        plan.setStartedOn(LocalDate.of(2026, 9, 28));
        PlannedMeal lunch = new PlannedMeal(DayOfWeek.MONDAY, MealType.LUNCH, "Comida");
        plan.addMeal(lunch);
        lunch.addDish(new PlannedDish("Ensalada", ownSalad, BigDecimal.ONE));
        lunch.addDish(new PlannedDish("Huevos revueltos", sharedEggs, BigDecimal.ONE));

        when(dietRepository.findWithMealsById(DIET_ID)).thenReturn(Optional.of(plan));
        when(dietRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(recipeService.writePrivate(anyList(), any(), any()))
                .thenAnswer(call -> ((List<RecipeDto>) call.getArgument(0)).stream()
                        .map(written -> recipe(null, written.name(), false))
                        .toList());
    }

    @Test
    void keepsTheRecipesPlatesStillNameAndDropsTheOnesNothingServes() {
        when(recipeService.linkable(eq(Set.of(8L)), eq(Set.of(7L))))
                .thenReturn(Map.of(8L, sharedEggs));

        dietService.update(DIET_ID, week(
                new Dish("Crema de calabacín", new BigDecimal("1.5"), null,
                        new RecipeDto(null, null, null, "Triturar.", "calabacín (200 g)",
                                List.of(), null), null),
                new Dish("Huevos revueltos", new BigDecimal("2"), 8L, null, null)));

        List<PlannedDish> dishes = plan.getMeals().get(0).getDishes();
        assertThat(dishes).extracting(PlannedDish::getName)
                .containsExactly("Crema de calabacín", "Huevos revueltos");
        // A plate's own recipe is called what the plate is, unless it says otherwise.
        assertThat(dishes.get(0).getRecipe().getName()).isEqualTo("Crema de calabacín");
        assertThat(dishes.get(0).getServings()).isEqualByComparingTo("1.5");
        assertThat(dishes.get(1).getRecipe()).isSameAs(sharedEggs);
        assertThat(dishes.get(1).getServings()).isEqualByComparingTo("2");
        // The salad's own recipe is served by nothing now; the shared one is never touched.
        verify(recipeService).deletePrivate(Set.of(7L));
    }

    @Test
    void keepsAPrivateRecipeAPlateNamesById() {
        when(recipeService.linkable(eq(Set.of(7L)), eq(Set.of(7L)))).thenReturn(Map.of(7L, ownSalad));

        dietService.update(DIET_ID, week(new Dish("Ensalada", BigDecimal.ONE, 7L, null, null)));

        assertThat(plan.getMeals().get(0).getDishes().get(0).getRecipe()).isSameAs(ownSalad);
        verify(recipeService).deletePrivate(Set.of());
    }

    @Test
    void refusesAPlateThatNamesARecipeAndWritesOne() {
        Dish both = new Dish("Ensalada", BigDecimal.ONE, 7L,
                new RecipeDto("Ensalada", "lechuga", List.of()), null);

        assertThatThrownBy(() -> dietService.update(DIET_ID, week(both)))
                .isInstanceOf(InvalidDietException.class);
        verify(recipeService, never()).deletePrivate(any());
    }

    private static DietRequestDto week(Dish... dishes) {
        return new DietRequestDto(PATIENT_ID, "Semana", LocalDate.of(2026, 9, 28), List.of(
                new DietDay(DayOfWeek.MONDAY, List.of(
                        new MealDto(MealType.LUNCH, "Comida", List.of(dishes))))));
    }

    private static Recipe recipe(Long id, String name, boolean library) {
        Recipe recipe = new Recipe(name, null, null, library);
        recipe.setId(id);
        return recipe;
    }
}
