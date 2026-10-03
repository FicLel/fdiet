package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.diet.dto.CopyDietRequestDto;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietMeasureSavedDto;
import com.fdiet.diet.dto.DietRationsDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.DietSettingsDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.dto.ParseDishRequestDto;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.dto.RecipeUsageDto;
import com.fdiet.diet.dto.ResolveIngredientDto;
import com.fdiet.reference.dto.MeasureCriterionRequestDto;
import com.fdiet.reference.dto.FoodMeasureDto;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Owns the stored diet: the {@code diets} row and the meals, dishes and
 * ingredients that hang off it. They are one aggregate — a dish exists only
 * inside its meal — so one service owns all four tables, and food is still
 * reached through the food module's own services, as the patient is through
 * {@code IPatientService}.
 *
 * <p>A diet belongs to one patient and each patient has one active diet, so the
 * calls split in two: the ones that ask about a person take a {@code patientId},
 * and the ones that address a diet by its own id do not, because an id is
 * already somebody's. Nothing here hides one patient's week from another — there
 * is no security layer, and this is a division of data, not of access.
 */
public interface IDietService {

    /**
     * Stores the submitted week as the patient's diet in force, archiving the
     * one it replaces. Ingredients no food matches exactly are stored unmatched.
     */
    DietDto create(DietRequestDto request);

    /**
     * Replaces the whole week of a stored diet. What is not sent is deleted.
     * The request's patient must be the one the diet already belongs to.
     */
    DietDto update(Long id, DietRequestDto request);

    /**
     * Writes a second diet holding the same week for another patient — its
     * days, its dishes, the sentences they were typed as and every food match
     * already made. The source is left exactly as it was, and the journal is
     * not copied: what one patient thought of a plate is their own record.
     */
    DietDto copy(Long id, CopyDietRequestDto request);

    /**
     * Deletes a diet, active or archived, with its week, its journal, its own
     * measure criteria and its private recipes. Library recipes and global
     * criteria stay. Deleting the diet in force leaves the patient with none;
     * no archived diet is reactivated.
     */
    void delete(Long id);

    /** The diet in force now for one patient. */
    DietDto findActive(Long patientId);

    /**
     * Every patient's diet in force, without their weeks — who is on a diet
     * right now, in one query. The patient selector is drawn from this.
     */
    List<DietSummaryDto> current();

    DietDto findById(Long id);

    /** One patient's archived diets, most recently started first. */
    PageDto<DietSummaryDto> history(Long patientId, int page, int size);

    /** Whether the diet is stored at all, without loading its week. */
    boolean exists(Long dietId);

    /**
     * The reference profile the diet is read against, or null when it has none,
     * without loading its week. Asked by {@code com.fdiet.journal}, which weighs
     * an extra by the same household measures the week is weighed by.
     */
    String referenceProfileCode(Long dietId);

    /**
     * Whether a dish sits at that place in the week — the day, the meal slot,
     * and its position within the meal.
     *
     * <p>Asked by {@code com.fdiet.journal}, which records what the patient
     * thought of a plate against the slot rather than against the dish row,
     * since a republish renumbers every row in the week. Only this service can
     * answer it: the dishes are its table.
     */
    boolean hasDishAt(Long dietId, DayOfWeek day, MealType mealType, int dishIndex);

    /**
     * A page of the diet's ingredients.
     *
     * @param resolved null for all of them, false for the ones still waiting to
     *                 be matched to a food, true for the rest
     * @param suggest  whether to attach the ranked candidates for each one,
     *                 which is what the fix-up screen is driven from
     */
    PageDto<DishIngredient> ingredients(
            Long dietId, Boolean resolved, boolean suggest, int page, int size);

    /** Matches one stored ingredient to a food, or corrects it. */
    DishIngredient resolveIngredient(Long dietId, Long ingredientId, ResolveIngredientDto change);

    /**
     * Reads recipe text into its ingredients, matched against the catalogues and
     * priced, without storing a thing.
     *
     * <p>The parsing a diet is written by lives in one place. An editor that
     * re-implemented it would drift from the importer, and the two would then
     * disagree about what the same line of text means.
     */
    RecipeDto parse(ParseDishRequestDto request);

    /** Changes a diet's name, profile or clinical mark without sending its week. */
    DietDto updateSettings(Long id, DietSettingsDto settings);

    /** The week counted in rations against its profile, or against {@code profileCode}. */
    DietRationsDto rations(Long dietId, String profileCode);

    /** The diet's own household-measure weights. */
    List<FoodMeasureDto> measures(Long dietId);

    /** Writes the diet's own weight for a measure and attaches it where it now weighs. */
    DietMeasureSavedDto saveMeasure(Long dietId, MeasureCriterionRequestDto request);

    /** Removes one of the diet's own measures; the ingredients it weighed are left unmeasured. */
    void deleteMeasure(Long dietId, Long measureId);

    /** Which plates serve a recipe, and in whose diets — asked before a library recipe is edited. */
    RecipeUsageDto recipeUsage(Long recipeId);
}
