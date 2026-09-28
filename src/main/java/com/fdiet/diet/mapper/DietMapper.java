package com.fdiet.diet.mapper;

import com.fdiet.diet.domain.Diet;
import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedIngredient;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.service.FoodMatch;
import com.fdiet.diet.service.IDietNutritionService;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DietMapper implements IDietMapper {

    private final IDietNutritionService dietNutritionService;
    private final IReferenceService referenceService;

    /**
     * The totals are worked out here because this is where the shape that
     * crosses the boundary is assembled, and the services handing them over sit
     * in the same layer — one level talking to itself, not a layer crossing.
     */
    public DietMapper(IDietNutritionService dietNutritionService,
                      IReferenceService referenceService) {
        this.dietNutritionService = dietNutritionService;
        this.referenceService = referenceService;
    }

    @Override
    public DietDto toDto(DietPlan plan) {
        return new DietDto(
                plan.getId(),
                plan.getPatient().getId(),
                plan.getPatient().getName(),
                plan.getName(),
                plan.getStatus(),
                plan.getStartedOn(),
                plan.getEndedOn(),
                plan.getReferenceProfileCode(),
                plan.isClinical(),
                toDays(plan),
                dietNutritionService.summarise(ingredientsOf(plan.getMeals())));
    }

    @Override
    public DietSummaryDto toSummary(DietPlan plan) {
        return new DietSummaryDto(
                plan.getId(),
                plan.getPatient().getId(),
                plan.getPatient().getName(),
                plan.getName(),
                plan.getStatus(),
                plan.getStartedOn(),
                plan.getEndedOn(),
                plan.getReferenceProfileCode(),
                plan.isClinical());
    }

    /**
     * The database hands the meals back in no particular order; the ordering
     * comes from feeding them through {@link Diet}, whose {@code EnumMap}s exist
     * for exactly that. It is the same class that orders a submitted week, so
     * a diet reads the same whether it has just been posted or just been read.
     *
     * <p>{@link Diet} rebuilds the days to order them and knows nothing of
     * totals, so each day's total is worked out here and put back afterwards.
     */
    @Override
    public List<DietDay> toDays(DietPlan plan) {
        Map<DayOfWeek, List<MealDto>> byDay = new LinkedHashMap<>();
        Map<DayOfWeek, List<PlannedIngredient>> ingredientsByDay = new LinkedHashMap<>();
        for (PlannedMeal meal : plan.getMeals()) {
            byDay.computeIfAbsent(meal.getDayOfWeek(), day -> new ArrayList<>()).add(toDto(meal));
            ingredientsByDay.computeIfAbsent(meal.getDayOfWeek(), day -> new ArrayList<>())
                    .addAll(ingredientsOf(List.of(meal)));
        }
        List<DietDay> days = byDay.entrySet().stream()
                .map(entry -> new DietDay(entry.getKey(), entry.getValue()))
                .toList();

        return new Diet(days).days().stream()
                .map(day -> new DietDay(day.day(), day.meals(), dietNutritionService.summarise(
                        ingredientsByDay.getOrDefault(day.day(), List.of()))))
                .toList();
    }

    @Override
    public MealDto toDto(PlannedMeal meal) {
        return new MealDto(
                meal.getType(),
                meal.getName(),
                meal.getDishes().stream().map(this::toDto).toList(),
                dietNutritionService.summarise(ingredientsOf(List.of(meal))));
    }

    @Override
    public Dish toDto(PlannedDish dish) {
        return new Dish(dish.getName(), dish.getRawText(),
                dish.getIngredients().stream().map(this::toDto).toList());
    }

    /**
     * A state disagreement is read against what the matched food's own name says
     * — {@code Lenteja, hervida} is cooked — and is shown, never converted: how
     * much 70 g of raw rice weighs once boiled is a yield factor somebody has to
     * choose.
     */
    @Override
    public DishIngredient toDto(PlannedIngredient ingredient) {
        FoodItem foodItem = ingredient.getFoodItem();
        BedcaFood bedcaFood = ingredient.getBedcaFood();
        ReferenceFoodMeasure measure = ingredient.getFoodMeasure();
        return new DishIngredient(
                ingredient.getId(),
                ingredient.getRawName(),
                ingredient.getQuantity(),
                ingredient.getUnit(),
                ingredient.getState(),
                ingredient.getSize(),
                foodItem == null ? null : foodItem.getId(),
                bedcaFood == null ? null : bedcaFood.getId(),
                measure == null ? null : measure.getId(),
                matchedNameOf(foodItem, bedcaFood),
                referenceService.describe(measure),
                bedcaFood != null && FoodState.disagree(
                        ingredient.getState(), FoodState.ofFoodName(bedcaFood.getName())),
                dietNutritionService.of(ingredient),
                null);
    }

    @Override
    public PlannedMeal toEntity(DayOfWeek day, MealDto meal) {
        return new PlannedMeal(day, meal.type(), meal.name());
    }

    @Override
    public PlannedDish toEntity(Dish dish) {
        return new PlannedDish(dish.name(), dish.rawText());
    }

    @Override
    public PlannedIngredient toEntity(DishIngredient ingredient, FoodMatch match,
                                      ReferenceFoodMeasure measure) {
        PlannedIngredient entity = new PlannedIngredient(
                ingredient.name(),
                match == null ? null : match.foodItem(),
                match == null ? null : match.bedcaFood(),
                ingredient.quantity(),
                ingredient.unit());
        entity.setState(ingredient.state());
        entity.setSize(ingredient.size());
        entity.setFoodMeasure(measure);
        return entity;
    }

    /** What the catalogue calls the food, so a matched row reads as matched. */
    private static String matchedNameOf(FoodItem foodItem, BedcaFood bedcaFood) {
        if (bedcaFood != null) {
            return bedcaFood.getName();
        }
        return foodItem == null ? null : foodItem.getCommercialName();
    }

    private static List<PlannedIngredient> ingredientsOf(Iterable<PlannedMeal> meals) {
        List<PlannedIngredient> ingredients = new ArrayList<>();
        for (PlannedMeal meal : meals) {
            for (PlannedDish dish : meal.getDishes()) {
                ingredients.addAll(dish.getIngredients());
            }
        }
        return ingredients;
    }
}
