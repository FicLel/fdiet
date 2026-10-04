package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.KeptMatchDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.dto.ResolveIngredientDto;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.reference.model.ReferenceFoodMeasure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns {@code recipes} and {@code recipe_ingredients}: the shared library a
 * nutritionist picks from, and the private recipes written inside one plate.
 *
 * <p>The diet reaches both tables only through here. It hands over the recipe ids
 * its plates serve wherever an ingredient is read or corrected, so a URL that
 * names one diet can never reach another's.
 */
public interface IRecipeService {

    /** The library, by name, a page at a time. */
    PageDto<RecipeDto> library(String name, int page, int size);

    RecipeDto byId(Long id);

    /** A new library recipe. Its name must not be one another library recipe holds. */
    RecipeDto create(RecipeDto request);

    /** Rewrites a library recipe — for every plate that serves it. */
    RecipeDto update(Long id, RecipeDto request);

    /** A library recipe no plate serves. One still on a plate is refused. */
    void delete(Long id);

    /**
     * Stores the private recipes of a week, matched and weighed in a handful of
     * batched calls. The answer is in the order asked.
     *
     * @param dietId  the diet whose own household measures apply, null for a new one
     * @param profile the reference profile the week is written against
     */
    List<Recipe> writePrivate(List<RecipeDto> contents, Long dietId, String profile);

    /**
     * The recipes a plate may point at by id: any library recipe, or one of
     * {@code ownPrivate} — the private recipes the diet already holds. Anything
     * else is somebody else's plate, and refused.
     */
    Map<Long, Recipe> linkable(Collection<Long> ids, Set<Long> ownPrivate);

    /** A private copy of a recipe with every match it carries — for a copied week. */
    Recipe copyPrivate(Recipe source);

    /** Points the ingredients weighed by a replaced measure at its replacement. */
    void repointMeasures(Collection<Recipe> recipes, Map<Long, ReferenceFoodMeasure> replacements);

    /** Removes these private recipes. Library ones among them are left alone. */
    void deletePrivate(Collection<Long> ids);

    PageDto<DishIngredient> ingredients(
            Collection<Long> recipeIds, Boolean resolved, boolean suggest, int page, int size);

    /**
     * Matches or corrects one ingredient of one of {@code recipeIds}. On a library
     * recipe the change reaches every plate that serves it, and the diet's own
     * measures do not apply — they would vanish with the diet.
     */
    DishIngredient resolveIngredient(Collection<Long> recipeIds, Long ingredientId,
                                     ResolveIngredientDto change, Long dietId, String profile);

    /**
     * One recipe read from text, matched and weighed the way a stored one is,
     * without storing anything.
     *
     * @param compositionFoodId the food a one-ingredient text names, when the caller
     *                          already knows it (the composer); null to match by name
     * @param preferredMeasure  the measure to keep for a one-ingredient text, when it fits
     * @param keep              matches a person already made, kept for every ingredient
     *                          still read under the same name (FD-048); empty for none
     */
    RecipeDto read(String text, String fallbackName, Long dietId, String profile,
                   Long compositionFoodId, Long preferredMeasure, List<KeptMatchDto> keep);

    /**
     * The ingredients of any recipe matched to this composition food whose measure
     * no person picked, with their recipes — what a criterion for the food may
     * re-weigh (FD-054). One query.
     */
    List<RecipeIngredient> autoMeasured(Long compositionFoodId);

    /** Stores ingredients whose measure was chosen again. One batched write. */
    void saveIngredients(Collection<RecipeIngredient> ingredients);
}
