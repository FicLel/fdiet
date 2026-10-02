package com.fdiet.diet.service;

import com.fdiet.diet.dto.CopyDietRequestDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.helpers.IMealTextParser;
import com.fdiet.diet.helpers.PortionScaler;
import com.fdiet.diet.mapper.IDietMapper;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.DietStatus;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.diet.repository.DietRepository;
import com.fdiet.diet.repository.PlannedDishRepository;
import com.fdiet.diet.repository.RecipeIngredientRepository;
import com.fdiet.diet.repository.RecipeRepository;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.patient.model.Patient;
import com.fdiet.patient.service.IPatientService;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Copying a week to another patient.
 *
 * <p>The assertions are mostly about what is <em>not</em> shared. A copy that
 * handed the same meal rows to two diets would put one nutritionist's edit into
 * another patient's week, and a copy that dropped the food matches would throw
 * away the part of an imported diet a person did by hand.
 */
class DietServiceCopyTest {

    private static final Long SOURCE_ID = 1L;
    private static final Long TARGET_PATIENT_ID = 9L;
    private static final String CELL = "Ensalada: lechuga (80 gr) + tomate (100 gr)";

    private final DietRepository dietRepository = mock(DietRepository.class);
    private final IPatientService patientService = mock(IPatientService.class);

    private final RecipeRepository recipeRepository = mock(RecipeRepository.class);

    /** The real one: what a copy carries is its work, so it is not mocked away. */
    private final RecipeService recipeService = new RecipeService(
            recipeRepository,
            mock(RecipeIngredientRepository.class),
            mock(IDietMapper.class),
            mock(IMealTextParser.class),
            mock(IFoodResolverService.class),
            mock(IFoodItemService.class),
            mock(IBedcaFoodService.class),
            mock(IReferenceService.class),
            new PortionScaler(),
            5);

    private final DietService dietService = new DietService(
            dietRepository,
            mock(PlannedDishRepository.class),
            mock(IDietMapper.class),
            recipeService,
            mock(IBedcaFoodService.class),
            patientService,
            mock(IReferenceService.class),
            new PortionScaler(),
            mock(IDietRationService.class));

    @Test
    void writesTheWeekAgainAsNewRowsOwnedByTheOtherPatient() {
        DietPlan source = sourceDiet();
        Patient target = patient(TARGET_PATIENT_ID, "Ana");
        given(source, target);

        dietService.copy(SOURCE_ID, new CopyDietRequestDto(
                TARGET_PATIENT_ID, "Semana de Ana", LocalDate.of(2026, 9, 7)));

        DietPlan copy = savedCopy();
        assertThat(copy.getPatient()).isSameAs(target);
        assertThat(copy.getName()).isEqualTo("Semana de Ana");
        assertThat(copy.getStatus()).isEqualTo(DietStatus.ACTIVE);
        assertThat(copy.getStartedOn()).isEqualTo(LocalDate.of(2026, 9, 7));

        // The same week, and none of the same rows: editing one diet must never
        // reach the other.
        assertThat(copy.getMeals()).hasSize(1);
        PlannedMeal meal = copy.getMeals().get(0);
        assertThat(meal).isNotSameAs(source.getMeals().get(0));
        assertThat(meal.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(meal.getType()).isEqualTo(MealType.LUNCH);
        assertThat(meal.getDiet()).isSameAs(copy);
        assertThat(source.getMeals().get(0).getDiet()).isSameAs(source);
    }

    @Test
    void keepsTheSourcesNameAndStartsTodayWhenNeitherIsGiven() {
        given(sourceDiet(), patient(TARGET_PATIENT_ID, "Ana"));

        dietService.copy(SOURCE_ID, new CopyDietRequestDto(TARGET_PATIENT_ID, null, null));

        assertThat(savedCopy().getName()).isEqualTo("Semana 1");
        assertThat(savedCopy().getStartedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    void carriesTheRecipeTextAndEveryMatchAlreadyMade() {
        DietPlan source = sourceDiet();
        given(source, patient(TARGET_PATIENT_ID, "Ana"));

        dietService.copy(SOURCE_ID, new CopyDietRequestDto(TARGET_PATIENT_ID, null, null));

        PlannedDish dish = savedCopy().getMeals().get(0).getDishes().get(0);
        assertThat(dish.getName()).isEqualTo("Ensalada");
        assertThat(dish.getServings()).isEqualByComparingTo("1.5");
        // A private recipe is copied, never shared: the two weeks are edited apart.
        Recipe sourceRecipe = source.getMeals().get(0).getDishes().get(0).getRecipe();
        assertThat(dish.getRecipe()).isNotSameAs(sourceRecipe);
        assertThat(dish.getRecipe().isLibrary()).isFalse();
        // The sentence the nutritionist typed travels. Rebuilding one from the
        // parts would be a different sentence claiming to be the original.
        assertThat(dish.getRecipe().getRawText()).isEqualTo(CELL);
        assertThat(dish.getRecipe().getSteps()).isEqualTo("Lavar y aliñar.");
        assertThat(dish.getIngredients()).hasSize(2);

        RecipeIngredient lettuce = dish.getIngredients().get(0);
        assertThat(lettuce.getRawName()).isEqualTo("lechuga");
        assertThat(lettuce.getQuantity()).isEqualByComparingTo("80");
        assertThat(lettuce.getUnit()).isEqualTo("gr");
        assertThat(lettuce.getPosition()).isZero();
        // The match a person made by hand is the work worth carrying over.
        assertThat(lettuce.getBedcaFood()).isSameAs(sourceIngredient(source, 0).getBedcaFood());

        // One nobody matched stays unmatched rather than being guessed at.
        assertThat(dish.getIngredients().get(1).isMatched()).isFalse();
        assertThat(dish.getIngredients().get(1).getPosition()).isEqualTo(1);
    }

    /** A library recipe is shared by every plate that serves it, the copy's included. */
    @Test
    void pointsAtALibraryRecipeRatherThanCopyingIt() {
        DietPlan source = sourceDiet();
        Recipe shared = source.getMeals().get(0).getDishes().get(0).getRecipe();
        shared.setLibrary(true);
        given(source, patient(TARGET_PATIENT_ID, "Ana"));

        dietService.copy(SOURCE_ID, new CopyDietRequestDto(TARGET_PATIENT_ID, null, null));

        assertThat(savedCopy().getMeals().get(0).getDishes().get(0).getRecipe()).isSameAs(shared);
        verify(recipeRepository, never()).save(any());
    }

    @Test
    void takesOnlyTheTargetsActiveSlotAndLeavesTheSourceAsItWas() {
        DietPlan source = sourceDiet();
        DietPlan targetsCurrent = new DietPlan();
        targetsCurrent.setStatus(DietStatus.ACTIVE);
        given(source, patient(TARGET_PATIENT_ID, "Ana"));
        when(dietRepository.findFirstByPatientIdAndStatus(TARGET_PATIENT_ID, DietStatus.ACTIVE))
                .thenReturn(Optional.of(targetsCurrent));

        dietService.copy(SOURCE_ID, new CopyDietRequestDto(TARGET_PATIENT_ID, null, null));

        // The target's own week is archived to free the slot uk_diets_active
        // lets one of their rows hold...
        assertThat(targetsCurrent.getStatus()).isEqualTo(DietStatus.ARCHIVED);
        assertThat(targetsCurrent.getEndedOn()).isEqualTo(LocalDate.now());
        verify(dietRepository).saveAndFlush(targetsCurrent);
        // ...and the diet being copied from is left exactly where it was.
        assertThat(source.getStatus()).isEqualTo(DietStatus.ACTIVE);
        assertThat(source.getEndedOn()).isNull();
    }

    @Test
    void refusesToCopyADietNothingCarries() {
        when(dietRepository.findWithMealsById(SOURCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dietService.copy(SOURCE_ID,
                new CopyDietRequestDto(TARGET_PATIENT_ID, null, null)))
                .isInstanceOf(DietNotFoundException.class);
        verify(dietRepository, never()).save(any());
    }

    private void given(DietPlan source, Patient target) {
        when(dietRepository.findWithMealsById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(patientService.entityById(TARGET_PATIENT_ID)).thenReturn(target);
        when(dietRepository.findFirstByPatientIdAndStatus(TARGET_PATIENT_ID, DietStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(dietRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(recipeRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    /** The plan the service was asked to store, which is the copy itself. */
    private DietPlan savedCopy() {
        ArgumentCaptor<DietPlan> captor = ArgumentCaptor.forClass(DietPlan.class);
        verify(dietRepository).save(captor.capture());
        return captor.getValue();
    }

    private static RecipeIngredient sourceIngredient(DietPlan source, int at) {
        return source.getMeals().get(0).getDishes().get(0).getIngredients().get(at);
    }

    /** One Monday lunch: a matched ingredient and an unmatched one. */
    private static DietPlan sourceDiet() {
        DietPlan plan = new DietPlan();
        plan.setId(SOURCE_ID);
        plan.setPatient(patient(1L, "Victor"));
        plan.setName("Semana 1");
        plan.setStatus(DietStatus.ACTIVE);
        plan.setStartedOn(LocalDate.of(2026, 8, 31));

        PlannedMeal meal = new PlannedMeal(DayOfWeek.MONDAY, MealType.LUNCH, "Comida");
        plan.addMeal(meal);
        Recipe recipe = new Recipe("Ensalada", CELL, "Lavar y aliñar.", false);
        recipe.setId(7L);
        meal.addDish(new PlannedDish("Ensalada", recipe, new BigDecimal("1.5")));

        BedcaFood lettuce = new BedcaFood();
        lettuce.setId(42L);
        lettuce.setName("Lechuga");
        recipe.addIngredient(new RecipeIngredient(
                "lechuga", null, lettuce, new BigDecimal("80"), "gr"));
        recipe.addIngredient(new RecipeIngredient(
                "tomate", null, null, new BigDecimal("100"), "gr"));
        return plan;
    }

    private static Patient patient(Long id, String name) {
        Patient patient = new Patient(name, null);
        patient.setId(id);
        return patient;
    }
}
