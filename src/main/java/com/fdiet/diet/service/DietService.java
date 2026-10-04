package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.diet.domain.Diet;
import com.fdiet.diet.dto.CopyDietRequestDto;
import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietMeasureSavedDto;
import com.fdiet.diet.dto.DietRationsDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.DietSettingsDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.dto.ParseDishRequestDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.dto.RecipeUsageDto;
import com.fdiet.diet.dto.ResolveIngredientDto;
import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.mapper.IDietMapper;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.DietStatus;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.diet.repository.DietRepository;
import com.fdiet.diet.repository.PlannedDishRepository;
import com.fdiet.patient.model.Patient;
import com.fdiet.patient.service.IPatientService;
import com.fdiet.reference.dto.MeasureCriterionRequestDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DietService implements IDietService {

    private static final int NAME_MAX = 255;

    private final DietRepository dietRepository;
    private final PlannedDishRepository dishRepository;
    private final IDietMapper dietMapper;
    private final IRecipeService recipeService;
    private final IPatientService patientService;
    private final IReferenceService referenceService;
    private final IDietRationService rationService;

    public DietService(DietRepository dietRepository,
                       PlannedDishRepository dishRepository,
                       IDietMapper dietMapper,
                       IRecipeService recipeService,
                       IPatientService patientService,
                       IReferenceService referenceService,
                       IDietRationService rationService) {
        this.dietRepository = dietRepository;
        this.dishRepository = dishRepository;
        this.dietMapper = dietMapper;
        this.recipeService = recipeService;
        this.patientService = patientService;
        this.referenceService = referenceService;
        this.rationService = rationService;
    }

    /**
     * A new week takes the profile it names, or — when it names none — the one
     * suggested for the patient's age: the adult profile for an adult or for a
     * patient with no birth date, a school band for a child, none below the
     * youngest band any loaded source covers. The suggestion is stored as the
     * choice, visible and changeable, never re-applied behind anybody's back.
     */
    @Override
    @Transactional
    public DietDto create(DietRequestDto request) {
        List<DietDay> week = validated(request);

        // Through the owning service, so a patient id nothing carries comes back
        // as that module's 404 rather than as a foreign key violation.
        Patient patient = patientService.entityById(request.patientId());
        // Null asks for the profile the patient's age suggests; a blank is a
        // person saying "none".
        String profile = request.referenceProfileCode() == null
                ? referenceService.suggestedProfileCode(patient.ageInMonths(LocalDate.now()))
                : request.referenceProfileCode().isBlank()
                        ? null
                        : requireProfile(request.referenceProfileCode());
        archiveActive(patient.getId());

        DietPlan plan = new DietPlan();
        plan.setPatient(patient);
        plan.setName(request.name());
        plan.setStatus(DietStatus.ACTIVE);
        plan.setStartedOn(request.startedOn());
        plan.setReferenceProfileCode(profile);
        plan.setClinical(Boolean.TRUE.equals(request.clinical()));
        fill(plan, week, Set.of(), null, profile);

        return dietMapper.toDto(dietRepository.save(plan));
    }

    /**
     * The week is replaced wholesale. The old meals are deleted in their own
     * flush before the new ones are written: Hibernate orders its inserts ahead
     * of its deletes, and {@code uk_diet_meals_slot} would reject the new
     * Monday breakfast while the old one is still there.
     *
     * <p>The private recipes of the old week go with it, except those a plate of
     * the new one kept by id. Library recipes are never touched: other weeks
     * serve them.
     */
    @Override
    @Transactional
    public DietDto update(Long id, DietRequestDto request) {
        List<DietDay> week = validated(request);

        DietPlan plan = dietRepository.findWithMealsById(id)
                .orElseThrow(() -> DietNotFoundException.diet(id));
        requireSamePatient(plan, request.patientId());
        plan.setName(request.name());
        plan.setStartedOn(request.startedOn());
        if (request.referenceProfileCode() != null) {
            plan.setReferenceProfileCode(request.referenceProfileCode().isBlank()
                    ? null
                    : requireProfile(request.referenceProfileCode()));
        }
        if (request.clinical() != null) {
            plan.setClinical(request.clinical());
        }
        Set<Long> ownPrivate = privateRecipeIds(plan);

        plan.getMeals().clear();
        dietRepository.saveAndFlush(plan);

        Set<Long> kept = fill(plan, week, ownPrivate, plan.getId(), plan.getReferenceProfileCode());
        DietPlan saved = dietRepository.saveAndFlush(plan);
        Set<Long> dropped = new HashSet<>(ownPrivate);
        dropped.removeAll(kept);
        recipeService.deletePrivate(dropped);
        return dietMapper.toDto(saved);
    }

    @Override
    @Transactional
    public DietDto updateSettings(Long id, DietSettingsDto settings) {
        DietPlan plan = dietRepository.findWithMealsById(id)
                .orElseThrow(() -> DietNotFoundException.diet(id));
        String name = Texts.clean(settings.name(), NAME_MAX);
        if (name != null) {
            plan.setName(name);
        }
        if (settings.referenceProfileCode() != null) {
            // Null leaves the profile alone; a blank takes it off, so the week
            // is read against nothing.
            plan.setReferenceProfileCode(settings.referenceProfileCode().isBlank()
                    ? null
                    : requireProfile(settings.referenceProfileCode()));
        }
        if (settings.clinical() != null) {
            plan.setClinical(settings.clinical());
        }
        return dietMapper.toDto(dietRepository.save(plan));
    }

    /**
     * The same week, written a second time for somebody else.
     *
     * <p>The rows are built afresh rather than shared: the two diets are edited
     * and republished independently from here on, and a shared meal would make
     * one nutritionist's edit land in another patient's week. A private recipe is
     * copied for the same reason; a library recipe is shared, as it is by every
     * plate that serves it. What does carry over is every food match already made — that is most of the work in an
     * imported diet, and re-matching by name would throw away the ones a person
     * decided by hand — and the household measures that weigh the ingredients,
     * including the source diet's own criteria, which are written again for the
     * copy so the two can be changed apart.
     *
     * <p>The journal does not come with it. What one patient thought of a plate,
     * and what they ate beside it, is their own record and hangs off their own
     * diet id.
     */
    @Override
    @Transactional
    public DietDto copy(Long id, CopyDietRequestDto request) {
        DietPlan source = dietRepository.findWithMealsById(id)
                .orElseThrow(() -> DietNotFoundException.diet(id));
        Patient target = patientService.entityById(request.patientId());

        // Frees the target's active slot — which may be the source's own, when a
        // week is being copied forward for the same patient.
        archiveActive(target.getId());

        DietPlan copy = new DietPlan();
        copy.setPatient(target);
        copy.setName(request.name() == null ? source.getName() : request.name());
        copy.setStatus(DietStatus.ACTIVE);
        copy.setStartedOn(request.startedOn() == null ? LocalDate.now() : request.startedOn());
        copy.setReferenceProfileCode(source.getReferenceProfileCode());
        copy.setClinical(source.isClinical());
        boolean ownMeasures = false;
        Map<Long, Recipe> copies = new HashMap<>();
        for (PlannedMeal meal : source.getMeals()) {
            PlannedMeal copiedMeal =
                    new PlannedMeal(meal.getDayOfWeek(), meal.getType(), meal.getName());
            copy.addMeal(copiedMeal);
            for (PlannedDish dish : meal.getDishes()) {
                Recipe recipe = dish.getRecipe();
                Recipe served = recipe == null || recipe.isLibrary()
                        ? recipe
                        : copies.computeIfAbsent(recipe.getId(), key -> recipeService.copyPrivate(recipe));
                ownMeasures |= recipe != null && !recipe.isLibrary() && weighedByOwnMeasure(recipe);
                copiedMeal.addDish(new PlannedDish(dish.getName(), served, dish.getServings()));
            }
        }
        DietPlan saved = dietRepository.save(copy);
        if (ownMeasures) {
            repointOwnMeasures(source.getId(), saved.getId(), copies.values());
        }
        return dietMapper.toDto(saved);
    }

    /**
     * One DELETE of the {@code diets} row; the schema takes the rest with it —
     * meals, dishes, scores, extras and the diet's own measure criteria are all
     * {@code ON DELETE CASCADE}. The recipe ids are read first, while the plates
     * still say which recipes they serve, and the private ones go after the
     * plates have released them ({@code fk_diet_dishes_recipe} would refuse
     * otherwise). Library recipes and global criteria are never touched.
     *
     * <p>Deleting the diet in force leaves the patient with none: no archived
     * diet is brought back, because which one to resume is a person's decision.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        List<Long> recipeIds = dishRepository.recipeIdsOf(id);
        if (dietRepository.deleteWithWeekById(id) == 0) {
            throw DietNotFoundException.diet(id);
        }
        recipeService.deletePrivate(recipeIds);
    }

    @Override
    @Transactional(readOnly = true)
    public DietDto findActive(Long patientId) {
        requirePatient(patientId);
        return dietRepository.findFirstByPatientIdAndStatus(patientId, DietStatus.ACTIVE)
                .map(dietMapper::toDto)
                .orElseThrow(() -> DietNotFoundException.noActiveDiet(patientId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DietSummaryDto> current() {
        return dietRepository.findByStatusOrderByPatientNameAsc(DietStatus.ACTIVE).stream()
                .map(dietMapper::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DietDto findById(Long id) {
        return dietRepository.findWithMealsById(id)
                .map(dietMapper::toDto)
                .orElseThrow(() -> DietNotFoundException.diet(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<DietSummaryDto> history(Long patientId, int page, int size) {
        requirePatient(patientId);
        Pageable pageable = PageRequest.of(page, size);
        Page<DietPlan> archived = dietRepository.findByPatientIdAndStatusNotOrderByStartedOnDesc(
                patientId, DietStatus.ACTIVE, pageable);
        return PageDto.of(archived, dietMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long dietId) {
        return dietId != null && dietRepository.existsById(dietId);
    }

    @Override
    @Transactional(readOnly = true)
    public String referenceProfileCode(Long dietId) {
        return profileOf(dietId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDishAt(Long dietId, DayOfWeek day, MealType mealType, int dishIndex) {
        if (dietId == null || day == null || mealType == null || dishIndex < 0) {
            return false;
        }
        return dishRepository.existsAtSlot(dietId, day, mealType, dishIndex);
    }

    /**
     * A diet's ingredients are its plates' recipes' ingredients, library ones
     * included: the fix-up list is where the nutritionist finds what is not
     * matched yet, and an unmatched ingredient of a shared recipe is unmatched in
     * this week too.
     */
    @Override
    @Transactional(readOnly = true)
    public PageDto<DishIngredient> ingredients(
            Long dietId, Boolean resolved, boolean suggest, int page, int size) {
        requireDiet(dietId);
        return recipeService.ingredients(dishRepository.recipeIdsOf(dietId), resolved, suggest,
                page, size);
    }

    @Override
    @Transactional
    public DishIngredient resolveIngredient(
            Long dietId, Long ingredientId, ResolveIngredientDto change) {
        DietPlan plan = dietRepository.findById(dietId)
                .orElseThrow(() -> DietNotFoundException.diet(dietId));
        return recipeService.resolveIngredient(dishRepository.recipeIdsOf(dietId), ingredientId,
                change, dietId, plan.getReferenceProfileCode());
    }

    /**
     * Recipe text, read the way the workbook import reads a cell. Nothing is
     * stored. The description of a plate is never read: it is what the patient
     * sees, and no food is taken out of it.
     */
    @Override
    @Transactional(readOnly = true)
    public RecipeDto parse(ParseDishRequestDto request) {
        return recipeService.read(request.text(), request.slotName(), request.dietId(),
                profileOf(request.dietId()), null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public DietRationsDto rations(Long dietId, String profileCode) {
        DietPlan plan = dietRepository.findWithMealsById(dietId)
                .orElseThrow(() -> DietNotFoundException.diet(dietId));
        if (profileCode != null) {
            requireProfile(profileCode);
        }
        return rationService.account(plan, profileCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodMeasureDto> measures(Long dietId) {
        requireDiet(dietId);
        return referenceService.dietMeasures(dietId);
    }

    /**
     * Writes the diet's own weight for a measure, then attaches it to every
     * stored ingredient of the diet's private recipes it can weigh. A library
     * recipe is left alone: it is shared, and one diet's criterion is not
     * everybody's.
     */
    @Override
    @Transactional
    public DietMeasureSavedDto saveMeasure(Long dietId, MeasureCriterionRequestDto request) {
        DietPlan plan = dietRepository.findById(dietId)
                .orElseThrow(() -> DietNotFoundException.diet(dietId));
        FoodMeasureDto saved = referenceService.saveDietMeasure(dietId, request);
        int attached = recipeService.attachDietMeasure(dishRepository.recipeIdsOf(dietId),
                request.compositionFoodId(), request.measure(), dietId, plan.getReferenceProfileCode());
        return new DietMeasureSavedDto(saved, attached);
    }

    @Override
    @Transactional
    public void deleteMeasure(Long dietId, Long measureId) {
        requireDiet(dietId);
        referenceService.deleteDietMeasure(dietId, measureId);
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeUsageDto recipeUsage(Long recipeId) {
        RecipeDto recipe = recipeService.byId(recipeId);
        return new RecipeUsageDto(recipe.id(), dishRepository.countByRecipeId(recipeId),
                dietRepository.findServingRecipe(recipeId).stream()
                        .map(dietMapper::toSummary)
                        .toList());
    }

    private String profileOf(Long dietId) {
        if (dietId == null) {
            return null;
        }
        return dietRepository.findById(dietId)
                .orElseThrow(() -> DietNotFoundException.diet(dietId))
                .getReferenceProfileCode();
    }

    /** Runs the week through the in-memory rules and hands it back ordered. */
    private List<DietDay> validated(DietRequestDto request) {
        return new Diet(request.days()).days();
    }

    private void requireDiet(Long dietId) {
        if (!dietRepository.existsById(dietId)) {
            throw DietNotFoundException.diet(dietId);
        }
    }

    private String requireProfile(String code) {
        if (!referenceService.profileExists(code)) {
            throw new InvalidDietException("No reference profile with code " + code
                    + ". GET /api/reference/profiles lists the ones a diet can be written against");
        }
        return code;
    }

    /** An unknown patient is that module's 404, not an empty listing. */
    private void requirePatient(Long patientId) {
        patientService.entityById(patientId);
    }

    /**
     * A diet does not change hands through an edit. Moving one would take the
     * patient's journal with it — the scores and the off-plan entries hang off
     * the diet id — so the week would arrive under a new name carrying somebody
     * else's opinion of it. {@code copy} is the answer, and it leaves both.
     */
    private static void requireSamePatient(DietPlan plan, Long patientId) {
        if (!plan.getPatient().getId().equals(patientId)) {
            throw new InvalidDietException("Diet " + plan.getId() + " belongs to patient "
                    + plan.getPatient().getId() + " and cannot be moved to patient " + patientId
                    + " by replacing its week. Copy it instead: POST /api/diets/"
                    + plan.getId() + "/copy");
        }
    }

    /**
     * Frees the patient's active slot, which {@code uk_diets_active} allows only
     * one of their rows to hold. Flushed on its own so the UPDATE reaches the
     * database before the INSERT of the diet replacing it.
     */
    private void archiveActive(Long patientId) {
        Optional<DietPlan> active =
                dietRepository.findFirstByPatientIdAndStatus(patientId, DietStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        DietPlan plan = active.get();
        plan.setStatus(DietStatus.ARCHIVED);
        plan.setEndedOn(LocalDate.now());
        dietRepository.saveAndFlush(plan);
    }

    /** The private recipes a stored week's plates hold. */
    private static Set<Long> privateRecipeIds(DietPlan plan) {
        return plan.getMeals().stream()
                .flatMap(meal -> meal.getDishes().stream())
                .map(PlannedDish::getRecipe)
                .filter(recipe -> recipe != null && !recipe.isLibrary())
                .map(Recipe::getId)
                .collect(Collectors.toSet());
    }

    private static boolean weighedByOwnMeasure(Recipe recipe) {
        return recipe.getIngredients().stream()
                .map(RecipeIngredient::getFoodMeasure)
                .anyMatch(measure -> measure != null && measure.isDietOwn());
    }

    /** The copy's ingredients weighed by the source diet's own measures point at the copy's. */
    private void repointOwnMeasures(Long sourceDietId, Long copyDietId, Collection<Recipe> recipes) {
        Map<Long, Long> copied = referenceService.copyDietMeasures(sourceDietId, copyDietId);
        Map<Long, ReferenceFoodMeasure> entities = referenceService.measureEntities(copied.values());
        Map<Long, ReferenceFoodMeasure> replacements = new HashMap<>();
        copied.forEach((from, to) -> replacements.put(from, entities.get(to)));
        recipeService.repointMeasures(recipes, replacements);
    }

    /**
     * Writes the plates of a week. A plate's own recipe is written as a new
     * private recipe — all of them in one batch, so the week's foods are matched
     * in a handful of calls — and a plate naming a recipe by id is pointed at it.
     *
     * @return the recipe ids plates pointed at, so the caller can tell which of
     *         the diet's own private recipes are still served
     */
    private Set<Long> fill(DietPlan plan, List<DietDay> week, Set<Long> ownPrivate, Long dietId,
                           String profile) {
        List<RecipeDto> written = new ArrayList<>();
        Set<Long> linked = new LinkedHashSet<>();
        for (Dish dish : dishesOf(week)) {
            if (dish.recipeId() != null && dish.recipe() != null) {
                throw new InvalidDietException("The plate \"" + dish.name() + "\" names a recipe "
                        + "by id and writes one of its own. Send recipeId or recipe, not both");
            }
            if (dish.recipe() != null) {
                written.add(named(dish));
            } else if (dish.recipeId() != null) {
                linked.add(dish.recipeId());
            }
        }
        Map<Long, Recipe> links = linked.isEmpty() ? Map.of() : recipeService.linkable(linked, ownPrivate);
        Iterator<Recipe> fresh = recipeService.writePrivate(written, dietId, profile).iterator();

        for (DietDay day : week) {
            for (MealDto meal : day.meals()) {
                PlannedMeal plannedMeal = dietMapper.toEntity(day.day(), meal);
                plan.addMeal(plannedMeal);
                for (Dish dish : meal.dishes()) {
                    Recipe recipe = dish.recipe() != null
                            ? fresh.next()
                            : dish.recipeId() == null ? null : links.get(dish.recipeId());
                    plannedMeal.addDish(new PlannedDish(dish.name(), recipe, dish.servings()));
                }
            }
        }
        return linked;
    }

    /** A plate's own recipe is called what the plate is, unless it says otherwise. */
    private static RecipeDto named(Dish dish) {
        RecipeDto recipe = dish.recipe();
        if (recipe.name() != null && !recipe.name().isBlank()) {
            return recipe;
        }
        return new RecipeDto(recipe.id(), dish.name(), false, recipe.steps(), recipe.rawText(),
                recipe.ingredients(), null);
    }

    private static List<Dish> dishesOf(List<DietDay> week) {
        List<Dish> dishes = new ArrayList<>();
        for (DietDay day : week) {
            for (MealDto meal : day.meals()) {
                dishes.addAll(meal.dishes());
            }
        }
        return dishes;
    }
}
