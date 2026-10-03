package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.dto.ResolveIngredientDto;
import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.helpers.IMealTextParser;
import com.fdiet.diet.mapper.IDietMapper;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.diet.repository.RecipeIngredientRepository;
import com.fdiet.diet.repository.RecipeRepository;
import com.fdiet.food.dto.FoodSuggestionDto;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.MeasureUser;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IMeasureUsageCounter;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The recipes, shared and private, and everything about their ingredients:
 * reading them from text, matching them to the catalogue, choosing the household
 * measure that weighs them, and correcting them one at a time.
 *
 * <p>A library recipe is weighed without any diet's own measures. Those belong to
 * one diet and are deleted with it, and a shared recipe weighed by one would go
 * quietly unweighed in every other week the day that diet went. An ingredient of
 * a library recipe that names one is refused, not silently re-weighed. The
 * nutritionist's global criteria belong to no diet, so they weigh a library
 * recipe as they weigh every week. Choosing the measure is
 * {@link IMeasureResolverService}'s; this service stores what it chose.
 */
@Service
public class RecipeService implements IRecipeService, IMeasureUsageCounter {

    private static final Sort BY_ID = Sort.by(Sort.Direction.ASC, "id");

    /** How many more candidates are ranked than shown, so a state disagreement can sink. */
    private static final int SUGGESTION_POOL = 3;

    private static final int NAME_MAX = 255;
    private static final int STEPS_MAX = 4000;
    private static final int TEXT_MAX = 1000;

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final IDietMapper dietMapper;
    private final IMealTextParser mealTextParser;
    private final IFoodResolverService foodResolverService;
    private final IFoodItemService foodItemService;
    private final IBedcaFoodService bedcaFoodService;
    private final IReferenceService referenceService;
    private final IMeasureResolverService measureResolver;
    private final int suggestionLimit;

    public RecipeService(RecipeRepository recipeRepository,
                         RecipeIngredientRepository ingredientRepository,
                         IDietMapper dietMapper,
                         IMealTextParser mealTextParser,
                         IFoodResolverService foodResolverService,
                         IFoodItemService foodItemService,
                         IBedcaFoodService bedcaFoodService,
                         IReferenceService referenceService,
                         IMeasureResolverService measureResolver,
                         @Value("${fdiet.diet.suggestion-limit:5}") int suggestionLimit) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.dietMapper = dietMapper;
        this.mealTextParser = mealTextParser;
        this.foodResolverService = foodResolverService;
        this.foodItemService = foodItemService;
        this.bedcaFoodService = bedcaFoodService;
        this.referenceService = referenceService;
        this.measureResolver = measureResolver;
        this.suggestionLimit = suggestionLimit;
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<RecipeDto> library(String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String wanted = Texts.trimToNull(name);
        Page<Recipe> found = wanted == null
                ? recipeRepository.findByLibraryTrueOrderByNameAsc(pageable)
                : recipeRepository.findByLibraryTrueAndNameContainingOrderByNameAsc(wanted, pageable);
        return PageDto.of(found, dietMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeDto byId(Long id) {
        return dietMapper.toDto(entityById(id));
    }

    @Override
    @Transactional
    public RecipeDto create(RecipeDto request) {
        String name = libraryName(request, null);
        Recipe recipe = new Recipe(name, Texts.clean(request.rawText(), TEXT_MAX),
                Texts.clean(request.steps(), STEPS_MAX), true);
        fill(recipe, ingredientsOf(request, name), null, null);
        return dietMapper.toDto(save(recipe));
    }

    @Override
    @Transactional
    public RecipeDto update(Long id, RecipeDto request) {
        Recipe recipe = entityById(id);
        if (!recipe.isLibrary()) {
            throw new InvalidDietException("Recipe " + id + " belongs to one plate and is "
                    + "rewritten with its week, not through the recipe library");
        }
        String name = libraryName(request, id);
        List<DishIngredient> ingredients = ingredientsOf(request, name);
        recipe.setName(name);
        recipe.setRawText(Texts.clean(request.rawText(), TEXT_MAX));
        recipe.setSteps(Texts.clean(request.steps(), STEPS_MAX));
        // Flushed on its own: the replacements take the positions the old rows hold.
        recipe.getIngredients().clear();
        recipeRepository.saveAndFlush(recipe);
        fill(recipe, ingredients, null, null);
        return dietMapper.toDto(save(recipe));
    }

    /**
     * {@code fk_diet_dishes_recipe} does not cascade on purpose: a recipe on
     * somebody's plate is part of their week, and deleting it from the library
     * should not empty the plate. The check is the foreign key itself — the plates
     * are the diet's table, not this service's.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Recipe recipe = entityById(id);
        if (!recipe.isLibrary()) {
            throw new InvalidDietException("Recipe " + id + " belongs to one plate and goes with it");
        }
        try {
            recipeRepository.delete(recipe);
            recipeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new InvalidDietException("Recipe " + id + " is still served on a plate. Take it "
                    + "off every diet that uses it first (GET /api/recipes/" + id + "/usage)");
        }
    }

    @Override
    @Transactional
    public List<Recipe> writePrivate(List<RecipeDto> contents, Long dietId, String profile) {
        List<Recipe> recipes = new ArrayList<>();
        List<List<DishIngredient>> written = new ArrayList<>();
        for (RecipeDto content : contents) {
            String name = Texts.clean(content.name(), NAME_MAX);
            recipes.add(new Recipe(name, Texts.clean(content.rawText(), TEXT_MAX),
                    Texts.clean(content.steps(), STEPS_MAX), false));
            written.add(ingredientsOf(content, name));
        }
        List<DishIngredient> all = written.stream().flatMap(List::stream).toList();
        Foods foods = foodsOf(all);
        Map<DishIngredient, ReferenceFoodMeasure> measures =
                measureResolver.measuresOf(all, foods::bedcaFood, dietId, profile);
        for (int at = 0; at < recipes.size(); at++) {
            for (DishIngredient ingredient : written.get(at)) {
                recipes.get(at).addIngredient(dietMapper.toEntity(
                        ingredient, foods.of(ingredient), measures.get(ingredient)));
            }
        }
        return recipeRepository.saveAll(recipes);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Recipe> linkable(Collection<Long> ids, Set<Long> ownPrivate) {
        Set<Long> asked = new LinkedHashSet<>(ids);
        Map<Long, Recipe> found = new HashMap<>();
        for (Recipe recipe : recipeRepository.findAllById(asked)) {
            found.put(recipe.getId(), recipe);
        }
        List<Long> refused = asked.stream()
                .filter(id -> !found.containsKey(id)
                        || !(found.get(id).isLibrary() || ownPrivate.contains(id)))
                .toList();
        if (!refused.isEmpty()) {
            throw new InvalidDietException("A plate points at a library recipe or at one this diet "
                    + "already holds. Unknown or somebody else's: " + refused);
        }
        return found;
    }

    @Override
    @Transactional
    public Recipe copyPrivate(Recipe source) {
        Recipe copy = new Recipe(source.getName(), source.getRawText(), source.getSteps(), false);
        for (RecipeIngredient ingredient : source.getIngredients()) {
            RecipeIngredient copied = new RecipeIngredient(
                    ingredient.getRawName(),
                    ingredient.getFoodItem(),
                    ingredient.getBedcaFood(),
                    ingredient.getQuantity(),
                    ingredient.getUnit());
            copied.setQuantityMax(ingredient.getQuantityMax());
            copied.setState(ingredient.getState());
            copied.setSize(ingredient.getSize());
            copied.setFoodMeasure(ingredient.getFoodMeasure());
            copy.addIngredient(copied);
        }
        return recipeRepository.save(copy);
    }

    @Override
    @Transactional
    public void repointMeasures(Collection<Recipe> recipes,
                                Map<Long, ReferenceFoodMeasure> replacements) {
        for (Recipe recipe : recipes) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                ReferenceFoodMeasure measure = ingredient.getFoodMeasure();
                if (measure != null && measure.isDietOwn()) {
                    ingredient.setFoodMeasure(replacements.get(measure.getId()));
                }
            }
        }
        recipeRepository.saveAll(recipes);
    }

    @Override
    @Transactional
    public void deletePrivate(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<Recipe> doomed = recipeRepository.findAllById(ids).stream()
                .filter(recipe -> !recipe.isLibrary())
                .toList();
        recipeRepository.deleteAll(doomed);
    }

    /**
     * The candidates are asked for per ingredient but cost no query: the
     * composition database is small enough to rank in memory, and only the page
     * being looked at is ranked.
     */
    @Override
    @Transactional(readOnly = true)
    public PageDto<DishIngredient> ingredients(
            Collection<Long> recipeIds, Boolean resolved, boolean suggest, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, BY_ID);
        if (recipeIds.isEmpty()) {
            return PageDto.of(Page.<RecipeIngredient>empty(pageable), dietMapper::toDto);
        }
        Page<RecipeIngredient> found;
        if (resolved == null) {
            found = ingredientRepository.findByRecipeIdIn(recipeIds, pageable);
        } else if (resolved) {
            found = ingredientRepository.findMatched(recipeIds, pageable);
        } else {
            found = ingredientRepository.findUnmatched(recipeIds, pageable);
        }
        return PageDto.of(found, ingredient -> withSuggestions(ingredient, suggest));
    }

    @Override
    @Transactional
    public DishIngredient resolveIngredient(Collection<Long> recipeIds, Long ingredientId,
                                            ResolveIngredientDto change, Long dietId,
                                            String profile) {
        if (change.foodItemId() != null && change.bedcaFoodId() != null) {
            throw new InvalidDietException(
                    "An ingredient points at one food: send foodItemId or bedcaFoodId, not both");
        }
        RecipeIngredient ingredient = recipeIds.isEmpty() ? null : ingredientRepository
                .findByIdAndRecipeIdIn(ingredientId, recipeIds).orElse(null);
        if (ingredient == null) {
            throw DietNotFoundException.ingredient(ingredientId);
        }
        boolean shared = ingredient.getRecipe().isLibrary();

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
            // One value sent is the range settled: the person chose where in it.
            ingredient.setQuantity(change.quantity());
            ingredient.setQuantityMax(null);
        }
        if (change.unit() != null) {
            ingredient.setUnit(change.unit());
        }

        if (shared && change.foodMeasureId() != null) {
            measureResolver.requireNoDietMeasures(List.of(change.foodMeasureId()));
        }
        Long preferred = change.foodMeasureId() != null
                ? change.foodMeasureId()
                : ingredient.getFoodMeasure() == null ? null : ingredient.getFoodMeasure().getId();
        MeasureChoiceDto choice = measureResolver.choose(ingredient.getBedcaFood(),
                ingredient.getUnit(), ingredient.getSize(), preferred,
                shared ? null : dietId, shared ? null : profile);
        if (change.foodMeasureId() != null
                && (choice.chosen() == null || !change.foodMeasureId().equals(choice.chosen().id()))) {
            throw new InvalidDietException("Household measure " + change.foodMeasureId()
                    + " does not weigh " + ingredient.getUnit() + " of this food. "
                    + "GET /api/reference/measures lists the ones that do");
        }
        ingredient.setFoodMeasure(measureResolver.entityOf(choice.chosen()));
        return dietMapper.toDto(ingredientRepository.save(ingredient));
    }

    /**
     * Nothing is stored: the ingredients are mapped through transient entities so
     * the editor is handed the same shape — matched name, scaled figures, the
     * measure that weighs it — that a stored ingredient comes back as.
     */
    @Override
    @Transactional(readOnly = true)
    public RecipeDto read(String text, String fallbackName, Long dietId, String profile,
                          Long preferredMeasure) {
        RecipeDto written = mealTextParser.parse(text, fallbackName);
        if (written == null) {
            throw new InvalidDietException("The text is blank; there is no recipe to read");
        }
        List<DishIngredient> ingredients = written.ingredients();
        if (preferredMeasure != null && ingredients.size() == 1) {
            ingredients = List.of(withMeasure(ingredients.get(0), preferredMeasure));
        }
        Foods foods = foodsOf(ingredients);
        Map<DishIngredient, ReferenceFoodMeasure> measures =
                measureResolver.measuresOf(ingredients, foods::bedcaFood, dietId, profile);

        Recipe transientRecipe = new Recipe(written.name(), written.rawText(), null, false);
        for (DishIngredient ingredient : ingredients) {
            transientRecipe.addIngredient(dietMapper.toEntity(
                    ingredient, foods.of(ingredient), measures.get(ingredient)));
        }
        return dietMapper.toDto(transientRecipe);
    }

    /**
     * Only private recipes: a library recipe is never weighed by one diet's
     * criterion. Goes through the same rule a publish would, so what the week
     * holds now is what it will hold after the next one.
     */
    @Override
    @Transactional
    public int attachDietMeasure(Collection<Long> recipeIds, Long bedcaFoodId,
                                 HouseholdMeasure measure, Long dietId, String profile) {
        if (recipeIds.isEmpty()) {
            return 0;
        }
        List<RecipeIngredient> candidates = ingredientRepository.findByRecipeIdIn(recipeIds).stream()
                .filter(ingredient -> !ingredient.getRecipe().isLibrary()
                        && ingredient.getBedcaFood() != null
                        && ingredient.getBedcaFood().getId().equals(bedcaFoodId)
                        && HouseholdMeasure.ofUnit(ingredient.getUnit())
                        .filter(written -> written == measure).isPresent())
                .toList();
        List<MeasureChoiceDto> choices = referenceService.chooseMeasures(candidates.stream()
                .map(ingredient -> new MeasureQueryDto(ingredient.getBedcaFood().getId(),
                        ingredient.getBedcaFood().getName(), ingredient.getUnit(), ingredient.getSize(),
                        null))
                .toList(), dietId, profile);
        Map<Long, ReferenceFoodMeasure> entities = referenceService.measureEntities(choices.stream()
                .map(MeasureChoiceDto::chosen).filter(Objects::nonNull).map(FoodMeasureDto::id)
                .toList());

        int attached = 0;
        for (int at = 0; at < candidates.size(); at++) {
            FoodMeasureDto chosen = choices.get(at).chosen();
            if (chosen != null && chosen.dietOwn()) {
                candidates.get(at).setFoodMeasure(entities.get(chosen.id()));
                attached++;
            }
        }
        ingredientRepository.saveAll(candidates);
        return attached;
    }

    @Override
    public MeasureUser user() {
        return MeasureUser.RECIPE_INGREDIENT;
    }

    /** One count query: how many recipe ingredients, of any recipe, a household measure weighs. */
    @Override
    @Transactional(readOnly = true)
    public long countUsing(Long measureId) {
        return ingredientRepository.countByFoodMeasureId(measureId);
    }

    private Recipe entityById(Long id) {
        return recipeRepository.findById(id).orElseThrow(() -> DietNotFoundException.recipe(id));
    }

    /** The unique index is underneath for the write this check races with. */
    private Recipe save(Recipe recipe) {
        try {
            return recipeRepository.saveAndFlush(recipe);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidDietException("There is already a recipe called " + recipe.getName()
                    + " in the library");
        }
    }

    /** A library recipe is found again by its name, so it has one and no other holds it. */
    private String libraryName(RecipeDto request, Long self) {
        String name = Texts.clean(request.name(), NAME_MAX);
        if (name == null) {
            throw new InvalidDietException("A library recipe needs a name to be found by");
        }
        recipeRepository.findFirstByLibraryTrueAndName(name)
                .filter(holder -> !holder.getId().equals(self))
                .ifPresent(holder -> {
                    throw new InvalidDietException("There is already a recipe called "
                            + holder.getName() + " in the library");
                });
        return name;
    }

    /**
     * The ingredients as sent — the editor sends them already read, with the
     * matches a person made — or, when none are, read from the text.
     */
    private List<DishIngredient> ingredientsOf(RecipeDto content, String name) {
        if (!content.ingredients().isEmpty()) {
            return content.ingredients();
        }
        String text = Texts.trimToNull(content.rawText());
        if (text == null) {
            return List.of();
        }
        RecipeDto read = mealTextParser.parse(text, name);
        return read == null ? List.of() : read.ingredients();
    }

    /** A library recipe's ingredients, matched and weighed without any diet's criteria. */
    private void fill(Recipe recipe, List<DishIngredient> ingredients, Long dietId, String profile) {
        measureResolver.requireNoDietMeasures(ingredients.stream()
                .map(DishIngredient::foodMeasureId).filter(Objects::nonNull).toList());
        Foods foods = foodsOf(ingredients);
        Map<DishIngredient, ReferenceFoodMeasure> measures =
                measureResolver.measuresOf(ingredients, foods::bedcaFood, dietId, profile);
        for (DishIngredient ingredient : ingredients) {
            recipe.addIngredient(dietMapper.toEntity(
                    ingredient, foods.of(ingredient), measures.get(ingredient)));
        }
    }

    /**
     * The composition database's best candidates, with any whose name states the
     * other side of raw/cooked from the text moved to the end: {@code lentejas
     * cocidas} is offered {@code Lenteja, hervida} before {@code Lenteja, seca,
     * cruda}. Still only an order — nothing is matched by it.
     */
    private DishIngredient withSuggestions(RecipeIngredient ingredient, boolean suggest) {
        DishIngredient dto = dietMapper.toDto(ingredient);
        if (!suggest || dto.resolved()) {
            return dto;
        }
        List<FoodSuggestionDto> ranked =
                bedcaFoodService.suggest(dto.name(), suggestionLimit * SUGGESTION_POOL);
        FoodState written = ingredient.getState();
        List<FoodSuggestionDto> ordered = new ArrayList<>(ranked);
        ordered.sort(Comparator.comparing((FoodSuggestionDto suggestion) ->
                FoodState.disagree(written, FoodState.ofFoodName(suggestion.name()))));
        return dto.withSuggestions(ordered.stream().limit(suggestionLimit).toList());
    }

    private static DishIngredient withMeasure(DishIngredient ingredient, Long measureId) {
        return new DishIngredient(ingredient.id(), ingredient.name(), ingredient.quantity(),
                ingredient.quantityMax(), ingredient.unit(), ingredient.state(), ingredient.size(),
                ingredient.foodItemId(), ingredient.bedcaFoodId(), measureId, ingredient.matchedName(),
                ingredient.measure(), ingredient.stateMismatch(), ingredient.yieldHint(),
                ingredient.nutrition(), ingredient.suggestions());
    }

    /**
     * Every food these ingredients need, fetched in a handful of batched calls
     * rather than one lookup per ingredient: the ones the caller named by id, and
     * the ones that have to be matched by name.
     */
    private Foods foodsOf(List<DishIngredient> ingredients) {
        Set<Long> itemIds = new LinkedHashSet<>();
        Set<Long> bedcaIds = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (DishIngredient ingredient : ingredients) {
            if (ingredient.bedcaFoodId() != null) {
                bedcaIds.add(ingredient.bedcaFoodId());
            } else if (ingredient.foodItemId() != null) {
                itemIds.add(ingredient.foodItemId());
            } else {
                names.add(ingredient.name());
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

        /** The composition-database food the ingredient found, or null. */
        BedcaFood bedcaFood(DishIngredient ingredient) {
            FoodMatch match = of(ingredient);
            return match == null ? null : match.bedcaFood();
        }
    }
}
