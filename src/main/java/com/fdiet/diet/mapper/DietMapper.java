package com.fdiet.diet.mapper;

import com.fdiet.diet.domain.Diet;
import com.fdiet.diet.domain.Serving;
import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.dto.YieldHintDto;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.diet.service.FoodMatch;
import com.fdiet.diet.service.IDietNutritionService;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.dto.YieldFactorDto;
import com.fdiet.reference.helpers.ReferenceMatcher;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DietMapper implements IDietMapper {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

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
                dietNutritionService.summarise(Serving.ofMeals(plan.getMeals())));
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
        Map<DayOfWeek, List<Serving>> servingsByDay = new LinkedHashMap<>();
        for (PlannedMeal meal : plan.getMeals()) {
            byDay.computeIfAbsent(meal.getDayOfWeek(), day -> new ArrayList<>()).add(toDto(meal));
            servingsByDay.computeIfAbsent(meal.getDayOfWeek(), day -> new ArrayList<>())
                    .addAll(Serving.ofMeals(List.of(meal)));
        }
        List<DietDay> days = byDay.entrySet().stream()
                .map(entry -> new DietDay(entry.getKey(), entry.getValue()))
                .toList();

        return new Diet(days).days().stream()
                .map(day -> new DietDay(day.day(), day.meals(), dietNutritionService.summarise(
                        servingsByDay.getOrDefault(day.day(), List.of()))))
                .toList();
    }

    @Override
    public MealDto toDto(PlannedMeal meal) {
        return new MealDto(
                meal.getType(),
                meal.getName(),
                meal.getDishes().stream().map(this::toDto).toList(),
                dietNutritionService.summarise(Serving.ofMeals(List.of(meal))));
    }

    @Override
    public Dish toDto(PlannedDish dish) {
        Recipe recipe = dish.getRecipe();
        return new Dish(dish.getName(), dish.getServings(),
                recipe == null ? null : recipe.getId(),
                recipe == null ? null : toDto(recipe),
                dietNutritionService.summarise(Serving.of(dish)));
    }

    /** One serving: the plate that serves it says how many. */
    @Override
    public RecipeDto toDto(Recipe recipe) {
        return new RecipeDto(
                recipe.getId(),
                recipe.getName(),
                recipe.isLibrary(),
                recipe.getSteps(),
                recipe.getRawText(),
                recipe.getIngredients().stream().map(this::toDto).toList(),
                dietNutritionService.summarise(Serving.single(recipe.getIngredients())));
    }

    /**
     * A state disagreement is read against what the matched food's Spanish name
     * says — {@code Lenteja, hervida} is cooked — and is shown, never converted: how
     * much 70 g of raw rice weighs once boiled is a yield factor somebody has to
     * choose.
     */
    @Override
    public DishIngredient toDto(RecipeIngredient ingredient) {
        FoodItem foodItem = ingredient.getFoodItem();
        CompositionFood food = ingredient.getCompositionFood();
        ReferenceFoodMeasure measure = ingredient.getFoodMeasure();
        boolean stateMismatch = ingredient.isStateMismatch();
        return new DishIngredient(
                ingredient.getId(),
                ingredient.getRawName(),
                ingredient.getQuantity(),
                ingredient.getQuantityMax(),
                ingredient.getUnit(),
                ingredient.getState(),
                ingredient.getSize(),
                foodItem == null ? null : foodItem.getId(),
                food == null ? null : food.getId(),
                measure == null ? null : measure.getId(),
                ingredient.measurePicked(),
                matchedNameOf(foodItem, food),
                food == null ? null : food.getSource(),
                referenceService.describe(measure),
                stateMismatch,
                stateMismatch ? yieldHint(ingredient, food) : null,
                dietNutritionService.of(ingredient),
                null,
                null);
    }

    @Override
    public PlannedMeal toEntity(DayOfWeek day, MealDto meal) {
        return new PlannedMeal(day, meal.type(), meal.name());
    }

    @Override
    public RecipeIngredient toEntity(DishIngredient ingredient, FoodMatch match,
                                      ReferenceFoodMeasure measure) {
        RecipeIngredient entity = new RecipeIngredient(
                ingredient.name(),
                match == null ? null : match.foodItem(),
                match == null ? null : match.compositionFood(),
                ingredient.quantity(),
                ingredient.unit());
        entity.setQuantityMax(ingredient.quantityMax());
        entity.setState(ingredient.state());
        entity.setSize(ingredient.size());
        entity.setFoodMeasure(measure);
        // Picked only when the measure attached is the one the person picked: a
        // pick the rule dropped (FD-039) leaves the rule's choice behind.
        entity.setMeasurePicked(measure != null && measure.getId() != null
                && measure.getId().equals(ingredient.pickedMeasureId()));
        return entity;
    }

    /**
     * The nearest published cooking yield for an ingredient weighed in one state
     * and matched to a food published in the other, and what its quantity comes
     * to in the food's state. The cooking method is read off whichever side is the
     * cooked one: the food's name ({@code Pollo, pechuga, plancha}) when the text
     * said raw, the text ({@code pechuga a la plancha (120 g)}) when the food is raw.
     */
    private YieldHintDto yieldHint(RecipeIngredient ingredient, CompositionFood food) {
        FoodState written = ingredient.getState();
        FoodState published = FoodState.ofFoodName(food.getNameEs());
        boolean toCooked = written.uncooked() && published.cooked();
        String methodText = toCooked ? food.getNameEs() : ingredient.getRawName();
        List<YieldFactorDto> yields = referenceService.yieldFactors(food.getNameEs(), methodText);
        if (yields.isEmpty()) {
            return null;
        }
        YieldFactorDto best = yields.get(0);
        BigDecimal grams = dietNutritionService.edibleGrams(ingredient);
        BigDecimal equivalent = grams == null ? null : toCooked
                ? grams.multiply(best.yieldPct()).divide(HUNDRED, 0, RoundingMode.HALF_UP)
                : grams.multiply(HUNDRED).divide(best.yieldPct(), 0, RoundingMode.HALF_UP);
        return new YieldHintDto(written, published, grams, equivalent, best.yieldPct(),
                best.foodLabel(), best.method(), ReferenceMatcher.namesMethod(best, methodText),
                best.sourceShortName(), best.pageRef());
    }

    /** What the catalogue calls the food, so a matched row reads as matched. */
    private static String matchedNameOf(FoodItem foodItem, CompositionFood food) {
        if (food != null) {
            return food.label();
        }
        return foodItem == null ? null : foodItem.getCommercialName();
    }
}
