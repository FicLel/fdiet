package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.diet.domain.Diet;
import com.fdiet.diet.dto.CopyDietRequestDto;
import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.dto.ParseDishRequestDto;
import com.fdiet.diet.dto.ResolveIngredientDto;
import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.helpers.IMealTextParser;
import com.fdiet.diet.mapper.IDietMapper;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.DietStatus;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedIngredient;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.repository.DietRepository;
import com.fdiet.diet.repository.PlannedDishRepository;
import com.fdiet.diet.repository.PlannedIngredientRepository;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.patient.model.Patient;
import com.fdiet.patient.service.IPatientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class DietService implements IDietService {

    private static final Sort BY_ID = Sort.by(Sort.Direction.ASC, "id");

    private final DietRepository dietRepository;
    private final PlannedIngredientRepository ingredientRepository;
    private final PlannedDishRepository dishRepository;
    private final IDietMapper dietMapper;
    private final IMealTextParser mealTextParser;
    private final IFoodResolverService foodResolverService;
    private final IFoodItemService foodItemService;
    private final IBedcaFoodService bedcaFoodService;
    private final IPatientService patientService;
    private final int suggestionLimit;

    public DietService(DietRepository dietRepository,
                       PlannedIngredientRepository ingredientRepository,
                       PlannedDishRepository dishRepository,
                       IDietMapper dietMapper,
                       IMealTextParser mealTextParser,
                       IFoodResolverService foodResolverService,
                       IFoodItemService foodItemService,
                       IBedcaFoodService bedcaFoodService,
                       IPatientService patientService,
                       @Value("${fdiet.diet.suggestion-limit:5}") int suggestionLimit) {
        this.dietRepository = dietRepository;
        this.ingredientRepository = ingredientRepository;
        this.dishRepository = dishRepository;
        this.dietMapper = dietMapper;
        this.mealTextParser = mealTextParser;
        this.foodResolverService = foodResolverService;
        this.foodItemService = foodItemService;
        this.bedcaFoodService = bedcaFoodService;
        this.patientService = patientService;
        this.suggestionLimit = suggestionLimit;
    }

    @Override
    @Transactional
    public DietDto create(DietRequestDto request) {
        List<DietDay> week = validated(request);
        Foods foods = foodsOf(week);

        // Through the owning service, so a patient id nothing carries comes back
        // as that module's 404 rather than as a foreign key violation.
        Patient patient = patientService.entityById(request.patientId());
        archiveActive(patient.getId());

        DietPlan plan = new DietPlan();
        plan.setPatient(patient);
        plan.setName(request.name());
        plan.setStatus(DietStatus.ACTIVE);
        plan.setStartedOn(request.startedOn());
        fill(plan, week, foods);

        return dietMapper.toDto(dietRepository.save(plan));
    }

    /**
     * The week is replaced wholesale. The old meals are deleted in their own
     * flush before the new ones are written: Hibernate orders its inserts ahead
     * of its deletes, and {@code uk_diet_meals_slot} would reject the new
     * Monday breakfast while the old one is still there.
     */
    @Override
    @Transactional
    public DietDto update(Long id, DietRequestDto request) {
        List<DietDay> week = validated(request);
        Foods foods = foodsOf(week);

        DietPlan plan = dietRepository.findWithMealsById(id)
                .orElseThrow(() -> DietNotFoundException.diet(id));
        requireSamePatient(plan, request.patientId());
        plan.setName(request.name());
        plan.setStartedOn(request.startedOn());

        plan.getMeals().clear();
        dietRepository.saveAndFlush(plan);

        fill(plan, week, foods);
        return dietMapper.toDto(dietRepository.save(plan));
    }

    /**
     * The same week, written a second time for somebody else.
     *
     * <p>The rows are built afresh rather than shared: the two diets are edited
     * and republished independently from here on, and a shared meal would make
     * one nutritionist's edit land in another patient's week. What does carry
     * over is every food match already made — that is most of the work in an
     * imported diet, and re-matching by name would throw away the ones a person
     * decided by hand.
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
        for (PlannedMeal meal : source.getMeals()) {
            PlannedMeal copiedMeal =
                    new PlannedMeal(meal.getDayOfWeek(), meal.getType(), meal.getName());
            copy.addMeal(copiedMeal);
            for (PlannedDish dish : meal.getDishes()) {
                PlannedDish copiedDish = new PlannedDish(dish.getName(), dish.getRawText());
                copiedMeal.addDish(copiedDish);
                for (PlannedIngredient ingredient : dish.getIngredients()) {
                    copiedDish.addIngredient(new PlannedIngredient(
                            ingredient.getRawName(),
                            ingredient.getFoodItem(),
                            ingredient.getBedcaFood(),
                            ingredient.getQuantity(),
                            ingredient.getUnit()));
                }
            }
        }
        return dietMapper.toDto(dietRepository.save(copy));
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
    public boolean hasDishAt(Long dietId, DayOfWeek day, MealType mealType, int dishIndex) {
        if (dietId == null || day == null || mealType == null || dishIndex < 0) {
            return false;
        }
        return dishRepository.existsAtSlot(dietId, day, mealType, dishIndex);
    }

    /**
     * The candidates are asked for per ingredient but cost no query: the
     * composition database is small enough to rank in memory, and only the page
     * being looked at is ranked.
     */
    @Override
    @Transactional(readOnly = true)
    public PageDto<DishIngredient> ingredients(
            Long dietId, Boolean resolved, boolean suggest, int page, int size) {
        requireDiet(dietId);
        Pageable pageable = PageRequest.of(page, size, BY_ID);
        Page<PlannedIngredient> found;
        if (resolved == null) {
            found = ingredientRepository.findByDishMealDietId(dietId, pageable);
        } else if (resolved) {
            found = ingredientRepository.findMatched(dietId, pageable);
        } else {
            found = ingredientRepository.findUnmatched(dietId, pageable);
        }
        return PageDto.of(found, ingredient -> withSuggestions(ingredient, suggest));
    }

    @Override
    @Transactional
    public DishIngredient resolveIngredient(
            Long dietId, Long ingredientId, ResolveIngredientDto change) {
        if (change.foodItemId() != null && change.bedcaFoodId() != null) {
            throw new InvalidDietException(
                    "An ingredient points at one food: send foodItemId or bedcaFoodId, not both");
        }
        PlannedIngredient ingredient = ingredientRepository
                .findByIdAndDishMealDietId(ingredientId, dietId)
                .orElseThrow(() -> DietNotFoundException.ingredient(ingredientId));

        // Matching to one half of the catalogue releases the other, so the row
        // never carries two foods at once.
        if (change.bedcaFoodId() != null) {
            ingredient.setBedcaFood(bedcaFoodService.entityById(change.bedcaFoodId()));
            ingredient.setFoodItem(null);
        }
        if (change.foodItemId() != null) {
            ingredient.setFoodItem(foodItemService.entityById(change.foodItemId()));
            ingredient.setBedcaFood(null);
        }
        if (change.name() != null) {
            ingredient.setRawName(change.name());
        }
        if (change.quantity() != null) {
            ingredient.setQuantity(change.quantity());
        }
        if (change.unit() != null) {
            ingredient.setUnit(change.unit());
        }
        return dietMapper.toDto(ingredientRepository.save(ingredient));
    }

    /**
     * One written cell, read the way the workbook import reads it. Nothing is
     * stored: the ingredients are mapped through transient entities so the
     * editor is handed the same shape — matched name, scaled figures — that a
     * stored ingredient comes back as, without a row existing for it.
     */
    @Override
    @Transactional(readOnly = true)
    public Dish parse(ParseDishRequestDto request) {
        Dish written = mealTextParser.parse(request.text(), request.slotName());
        if (written == null) {
            throw new InvalidDietException("The cell is blank; there is no dish to read");
        }
        Foods foods = foodsOf(List.of(
                new DietDay(DayOfWeek.MONDAY, List.of(new MealDto(
                        MealType.BREAKFAST, written.name(), List.of(written))))));

        List<DishIngredient> resolved = written.ingredients().stream()
                .map(ingredient -> dietMapper.toDto(
                        dietMapper.toEntity(ingredient, foods.of(ingredient))))
                .toList();
        return written.withIngredients(resolved);
    }

    private DishIngredient withSuggestions(PlannedIngredient ingredient, boolean suggest) {
        DishIngredient dto = dietMapper.toDto(ingredient);
        if (!suggest || dto.resolved()) {
            return dto;
        }
        return dto.withSuggestions(bedcaFoodService.suggest(dto.name(), suggestionLimit));
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

    private void fill(DietPlan plan, List<DietDay> week, Foods foods) {
        for (DietDay day : week) {
            for (MealDto meal : day.meals()) {
                PlannedMeal plannedMeal = dietMapper.toEntity(day.day(), meal);
                plan.addMeal(plannedMeal);
                for (Dish dish : meal.dishes()) {
                    PlannedDish plannedDish = dietMapper.toEntity(dish);
                    plannedMeal.addDish(plannedDish);
                    for (DishIngredient ingredient : dish.ingredients()) {
                        plannedDish.addIngredient(
                                dietMapper.toEntity(ingredient, foods.of(ingredient)));
                    }
                }
            }
        }
    }

    /**
     * Every food the week needs, fetched in a handful of batched calls rather
     * than one lookup per ingredient: the ones the caller named by id, and the
     * ones that have to be matched by name.
     */
    private Foods foodsOf(List<DietDay> week) {
        Set<Long> itemIds = new LinkedHashSet<>();
        Set<Long> bedcaIds = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (DietDay day : week) {
            for (MealDto meal : day.meals()) {
                for (Dish dish : meal.dishes()) {
                    for (DishIngredient ingredient : dish.ingredients()) {
                        if (ingredient.bedcaFoodId() != null) {
                            bedcaIds.add(ingredient.bedcaFoodId());
                        } else if (ingredient.foodItemId() != null) {
                            itemIds.add(ingredient.foodItemId());
                        } else {
                            names.add(ingredient.name());
                        }
                    }
                }
            }
        }

        Map<Long, FoodItem> items = foodItemService.entitiesByIds(itemIds);
        requireAllFound(itemIds, items.keySet(), "food items");
        Map<Long, BedcaFood> generic = bedcaFoodService.entitiesByIds(bedcaIds);
        requireAllFound(bedcaIds, generic.keySet(), "composition-database foods");

        return new Foods(items, generic, foodResolverService.resolve(names));
    }

    /** An id the caller made up is a mistake to report, not a food to guess at. */
    private static void requireAllFound(Set<Long> asked, Set<Long> found, String what) {
        List<Long> unknown = asked.stream().filter(id -> !found.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw new InvalidDietException("Unknown " + what + ": " + unknown);
        }
    }

    /**
     * The ways an ingredient finds its food: the id the caller gave, on either
     * half of the catalogue, or its name matched against both. None may find
     * one, and then the ingredient is stored unmatched.
     */
    private record Foods(Map<Long, FoodItem> items,
                         Map<Long, BedcaFood> generic,
                         Map<String, FoodMatch> byName) {

        FoodMatch of(DishIngredient ingredient) {
            if (ingredient.bedcaFoodId() != null) {
                return FoodMatch.of(generic.get(ingredient.bedcaFoodId()));
            }
            if (ingredient.foodItemId() != null) {
                return FoodMatch.of(items.get(ingredient.foodItemId()));
            }
            String key = Texts.normaliseName(ingredient.name());
            return key == null ? null : byName.get(key);
        }
    }
}
