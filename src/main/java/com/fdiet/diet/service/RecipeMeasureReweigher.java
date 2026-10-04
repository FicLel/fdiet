package com.fdiet.diet.service;

import com.fdiet.diet.dto.DietProfileDto;
import com.fdiet.diet.model.Recipe;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.reference.domain.MeasureScope;
import com.fdiet.reference.domain.MeasureUser;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.dto.ScopedMeasureQueryDto;
import com.fdiet.reference.helpers.Remeasure;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IMeasureReweigher;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The diet's answer to a criterion written in the reference module (FD-054):
 * the recipe ingredients it reaches whose measure nobody picked are weighed by
 * the rule again — in every week, archived ones included, as a publish of each
 * would weigh them, without waiting for one.
 *
 * <p>Owns no table. The ingredients are read and written through
 * {@link IRecipeService}, the diet each private recipe is served in through
 * {@link IDietService}; a separate bean because the recipe service cannot depend
 * on the diet service, which depends on it.
 *
 * <p>A private recipe is weighed inside its diet — its criteria and profile — and
 * a library recipe inside none, as {@code RecipeService} writes them. A diet's
 * criterion reaches only that diet's private recipes.
 */
@Service
public class RecipeMeasureReweigher implements IMeasureReweigher {

    private final IRecipeService recipeService;
    private final IDietService dietService;
    private final IReferenceService referenceService;

    public RecipeMeasureReweigher(IRecipeService recipeService, IDietService dietService,
                                  IReferenceService referenceService) {
        this.recipeService = recipeService;
        this.dietService = dietService;
        this.referenceService = referenceService;
    }

    @Override
    public MeasureUser user() {
        return MeasureUser.RECIPE_INGREDIENT;
    }

    /**
     * Five queries whatever the number of ingredients or diets — the ingredients,
     * their diets, every diet's criteria, the global ones and the rows chosen — and
     * one batched write. O(n) over the ingredients of the criterion's food.
     */
    @Override
    @Transactional
    public int reweigh(MeasureScope scope) {
        List<RecipeIngredient> candidates = recipeService.autoMeasured(scope.compositionFoodId()).stream()
                .filter(ingredient -> scope.measures(ingredient.getUnit()))
                .toList();
        if (candidates.isEmpty()) {
            return 0;
        }
        Map<Long, DietProfileDto> diets = dietService.dietsServingPrivate(candidates.stream()
                .map(RecipeIngredient::getRecipe)
                .filter(recipe -> !recipe.isLibrary())
                .map(Recipe::getId)
                .distinct()
                .toList());

        List<RecipeIngredient> reached = new ArrayList<>();
        List<ScopedMeasureQueryDto> queries = new ArrayList<>();
        for (RecipeIngredient ingredient : candidates) {
            Recipe recipe = ingredient.getRecipe();
            DietProfileDto diet = recipe.isLibrary() ? null : diets.get(recipe.getId());
            boolean inScope = scope.global()
                    ? recipe.isLibrary() || diet != null
                    : diet != null && scope.dietId().equals(diet.dietId());
            if (!inScope) {
                continue;
            }
            reached.add(ingredient);
            queries.add(new ScopedMeasureQueryDto(new MeasureQueryDto(ingredient.compositionFoodId(),
                    ingredient.getCompositionFood().getNameEs(), ingredient.getUnit(),
                    ingredient.getSize(), null),
                    diet == null ? null : diet.dietId(),
                    diet == null ? null : diet.profileCode()));
        }
        List<ReferenceFoodMeasure> chosen = referenceService.rechoose(queries);
        List<RecipeIngredient> changed = Remeasure.apply(reached, chosen,
                RecipeIngredient::getFoodMeasure, (ingredient, measure) -> {
                    ingredient.setFoodMeasure(measure);
                    ingredient.setMeasurePicked(false);
                });
        recipeService.saveIngredients(changed);
        return changed.size();
    }
}
