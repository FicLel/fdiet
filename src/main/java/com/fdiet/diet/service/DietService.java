package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.diet.domain.Diet;
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
    private final int suggestionLimit;

    public DietService(DietRepository dietRepository,
                       PlannedIngredientRepository ingredientRepository,
                       PlannedDishRepository dishRepository,
                       IDietMapper dietMapper,
                       IMealTextParser mealTextParser,
                       IFoodResolverService foodResolverService,
                       IFoodItemService foodItemService,
                       IBedcaFoodService bedcaFoodService,
                       @Value("${fdiet.diet.suggestion-limit:5}") int suggestionLimit) {
        this.dietRepository = dietRepository;
        this.ingredientRepository = ingredientRepository;
        this.dishRepository = dishRepository;
        this.dietMapper = dietMapper;
        this.mealTextParser = mealTextParser;
        this.foodResolverService = foodResolverService;
        this.foodItemService = foodItemService;
        this.bedcaFoodService = bedcaFoodService;
        this.suggestionLimit = suggestionLimit;
    }

    @Override
    @Transactional
    public DietDto create(DietRequestDto request) {
        List<DietDay> week = validated(request);
        Foods foods = foodsOf(week);

        archiveActive();

        DietPlan plan = new DietPlan();
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
        plan.setName(request.name());
        plan.setStartedOn(request.startedOn());

        plan.getMeals().clear();
        dietRepository.saveAndFlush(plan);

        fill(plan, week, foods);
        return dietMapper.toDto(dietRepository.save(plan));
    }

    @Override
    @Transactional(readOnly = true)
    public DietDto findActive() {
        return dietRepository.findFirstByStatus(DietStatus.ACTIVE)
                .map(dietMapper::toDto)
                .orElseThrow(DietNotFoundException::noActiveDiet);
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
    public PageDto<DietSummaryDto> history(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DietPlan> archived =
                dietRepository.findByStatusNotOrderByStartedOnDesc(DietStatus.ACTIVE, pageable);
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

    /**
     * Frees the single active slot, which {@code uk_diets_active} allows only
     * one row to hold. Flushed on its own so the UPDATE reaches the database
     * before the INSERT of the diet replacing it.
     */
    private void archiveActive() {
        Optional<DietPlan> active = dietRepository.findFirstByStatus(DietStatus.ACTIVE);
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
