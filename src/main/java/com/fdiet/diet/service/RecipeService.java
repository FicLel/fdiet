package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.KeptMatchDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.dto.ResolveIngredientDto;
import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.helpers.IMealTextParser;
import com.fdiet.diet.helpers.KeptMatches;
import com.fdiet.diet.mapper.IDietMapper;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.diet.repository.RecipeIngredientRepository;
import com.fdiet.diet.repository.RecipeRepository;
import com.fdiet.reference.domain.MeasureUser;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IMeasureUsageCounter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
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

    private static final int NAME_MAX = 255;
    private static final int STEPS_MAX = 4000;
    private static final int TEXT_MAX = 1000;

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final IDietMapper dietMapper;
    private final IMealTextParser mealTextParser;
    private final IIngredientFoodService ingredientFoods;
    private final IMeasureResolverService measureResolver;

    public RecipeService(RecipeRepository recipeRepository,
                         RecipeIngredientRepository ingredientRepository,
                         IDietMapper dietMapper,
                         IMealTextParser mealTextParser,
                         IIngredientFoodService ingredientFoods,
                         IMeasureResolverService measureResolver) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.dietMapper = dietMapper;
        this.mealTextParser = mealTextParser;
        this.ingredientFoods = ingredientFoods;
        this.measureResolver = measureResolver;
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
        IngredientFoods foods = ingredientFoods.foodsOf(all);
        Map<DishIngredient, ReferenceFoodMeasure> measures =
                measureResolver.measuresOf(all, foods::compositionFood, dietId, profile);
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
                    ingredient.getCompositionFood(),
                    ingredient.getQuantity(),
                    ingredient.getUnit());
            copied.setQuantityMax(ingredient.getQuantityMax());
            copied.setState(ingredient.getState());
            copied.setSize(ingredient.getSize());
            copied.setFoodMeasure(ingredient.getFoodMeasure());
            copied.setMeasurePicked(ingredient.measurePicked());
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
     * composition foods' Spanish names are ranked in memory, and only the page
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
        if (change.foodItemId() != null && change.compositionFoodId() != null) {
            throw new InvalidDietException(
                    "An ingredient points at one food: send foodItemId or compositionFoodId, not both");
        }
        RecipeIngredient ingredient = recipeIds.isEmpty() ? null : ingredientRepository
                .findByIdAndRecipeIdIn(ingredientId, recipeIds).orElse(null);
        if (ingredient == null) {
            throw DietNotFoundException.ingredient(ingredientId);
        }
        boolean shared = ingredient.getRecipe().isLibrary();

        // Matching to one half of the catalogue releases the other, so the row
        // never carries two foods at once.
        if (change.compositionFoodId() != null) {
            ingredient.setCompositionFood(ingredientFoods.compositionFood(change.compositionFoodId()));
            ingredient.setFoodItem(null);
        }
        if (change.foodItemId() != null) {
            ingredient.setFoodItem(ingredientFoods.foodItem(change.foodItemId()));
            ingredient.setCompositionFood(null);
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
        // A measure sent is a person's pick; one left out is kept only when a person
        // picked it before — the rule's own choice is made again (FD-054).
        Long preferred = change.foodMeasureId() != null
                ? change.foodMeasureId()
                : ingredient.measurePicked() ? ingredient.getFoodMeasure().getId() : null;
        MeasureChoiceDto choice = measureResolver.choose(ingredient.getCompositionFood(),
                ingredient.getUnit(), ingredient.getSize(), preferred,
                shared ? null : dietId, shared ? null : profile);
        if (change.foodMeasureId() != null
                && (choice.chosen() == null || !change.foodMeasureId().equals(choice.chosen().id()))) {
            throw new InvalidDietException("Household measure " + change.foodMeasureId()
                    + " does not weigh " + ingredient.getUnit() + " of this food. "
                    + "GET /api/reference/measures lists the ones that do");
        }
        ingredient.setFoodMeasure(measureResolver.entityOf(choice.chosen()));
        ingredient.setMeasurePicked(preferred != null && choice.chosen() != null
                && preferred.equals(choice.chosen().id()));
        return dietMapper.toDto(ingredientRepository.save(ingredient));
    }

    /**
     * Nothing is stored: the ingredients are mapped through transient entities so
     * the editor is handed the same shape — matched name, scaled figures, the
     * measure that weighs it — that a stored ingredient comes back as. A kept
     * match's food goes in by id, so its name is never sent to the resolver.
     */
    @Override
    @Transactional(readOnly = true)
    public RecipeDto read(String text, String fallbackName, Long dietId, String profile,
                          Long compositionFoodId, Long preferredMeasure, List<KeptMatchDto> keep) {
        RecipeDto written = mealTextParser.parse(text, fallbackName);
        if (written == null) {
            throw new InvalidDietException("The text is blank; there is no recipe to read");
        }
        List<DishIngredient> ingredients = KeptMatches.apply(written.ingredients(), keep);
        if (ingredients.size() == 1) {
            ingredients = List.of(ingredients.get(0).pinnedTo(compositionFoodId, preferredMeasure));
        }
        IngredientFoods foods = ingredientFoods.foodsOf(ingredients);
        Map<DishIngredient, ReferenceFoodMeasure> measures =
                measureResolver.measuresOf(ingredients, foods::compositionFood, dietId, profile);

        Recipe transientRecipe = new Recipe(written.name(), written.rawText(), null, false);
        for (DishIngredient ingredient : ingredients) {
            transientRecipe.addIngredient(dietMapper.toEntity(
                    ingredient, foods.of(ingredient), measures.get(ingredient)));
        }
        return dietMapper.toDto(transientRecipe);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeIngredient> autoMeasured(Long compositionFoodId) {
        return ingredientRepository.findAutoMeasured(compositionFoodId);
    }

    @Override
    @Transactional
    public void saveIngredients(Collection<RecipeIngredient> ingredients) {
        ingredientRepository.saveAll(ingredients);
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
                .map(DishIngredient::pickedMeasureId).filter(Objects::nonNull).toList());
        IngredientFoods foods = ingredientFoods.foodsOf(ingredients);
        Map<DishIngredient, ReferenceFoodMeasure> measures =
                measureResolver.measuresOf(ingredients, foods::compositionFood, dietId, profile);
        for (DishIngredient ingredient : ingredients) {
            recipe.addIngredient(dietMapper.toEntity(
                    ingredient, foods.of(ingredient), measures.get(ingredient)));
        }
    }

    /** The composition foods' best candidates for an ingredient nobody matched; only an order. */
    private DishIngredient withSuggestions(RecipeIngredient ingredient, boolean suggest) {
        DishIngredient dto = dietMapper.toDto(ingredient);
        if (!suggest || dto.resolved()) {
            return dto;
        }
        return dto.withSuggestions(ingredientFoods.suggestionsFor(dto.name(), ingredient.getState()));
    }
}
