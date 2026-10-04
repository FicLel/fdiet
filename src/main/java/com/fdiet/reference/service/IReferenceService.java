package com.fdiet.reference.service;

import com.fdiet.reference.dto.MeasureCriterionRequestDto;
import com.fdiet.reference.dto.MeasureCriterionSavedDto;
import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.HouseholdMeasureDto;
import com.fdiet.reference.dto.MealSharesDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.RecommendationDto;
import com.fdiet.reference.dto.ScopedMeasureQueryDto;
import com.fdiet.reference.dto.ReferenceProfileDetailDto;
import com.fdiet.reference.dto.ReferenceProfileDto;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto;
import com.fdiet.reference.dto.YieldFactorDto;
import com.fdiet.reference.model.ReferenceFoodMeasure;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The one way into the nutrition reference tables — sources, population bands,
 * rations, household measures, recommendations, meal shares and exchange
 * definitions. They are one catalogue loaded together from one set of files, so
 * one service owns all of them, the way {@code IDietService} owns a diet and
 * everything under it.
 *
 * <p>Nothing here is derived and stored. The published rows are small and read
 * on every weighing, so they are held in memory and dropped whenever a sync
 * writes; the nutritionist's own criteria (one diet's, and her global ones) are
 * read per request through {@link IMeasureCriterionService}.
 */
public interface IReferenceService {

    /** Every source, with its licence class and the attribution its figures need. */
    List<ReferenceSourceDto> sources();

    /**
     * The profiles a diet can be written against. With an age, the one suggested
     * for it is marked — an offer the nutritionist accepts or not.
     */
    List<ReferenceProfileDto> profiles(Integer ageMonths);

    ReferenceProfileDetailDto profile(String code);

    /**
     * The profile suggested for somebody this many months old: the default adult
     * profile for an adult or an unknown age, the band that states the age for a
     * child, and null when no loaded source covers it.
     */
    String suggestedProfileCode(Integer ageMonths);

    boolean profileExists(String code);

    /** The rations a profile defines. */
    List<RationDto> rations(String profileCode);

    /**
     * The rations that cover one food: the profile's own first, then any
     * published per-food ration of another source (5 al día), each labelled.
     * Without a food, the profile's rations.
     *
     * <p>The food is a composition food (CIQUAL 2025 / BLS 4.0): its id reaches
     * the rows naming it, and its Spanish name the family rows it fits.
     */
    List<RationDto> rationsForFood(String profileCode, Long compositionFoodId);

    /**
     * The one ration of the profile a food is counted in, or null when it is in
     * no group of the profile or the choice would be a guess. Answered in memory.
     *
     * @param compositionFoodId the composition food, or null for a food known by
     *                          its name only
     */
    RationDto countingRation(String profileCode, Long compositionFoodId, String foodName);

    List<RecommendationDto> recommendations(String profileCode);

    /** A profile's own description, or null when the code names none. */
    ReferenceProfileDto profileSummary(String code);

    /** The meal energy shares a profile is read against, or null when it has none. */
    MealSharesDto mealShares(String profileCode);

    /** Exchange definitions; the clinical ones only when asked for. */
    List<ExchangeSystemDto> exchangeSystems(boolean includeClinical);

    /**
     * The published cooking yields that could say what a food weighs raw or
     * cooked, most specific first, and those whose method {@code methodText}
     * names ahead of the rest. Offers only; answered in memory.
     */
    List<YieldFactorDto> yieldFactors(String foodName, String methodText);

    /**
     * The same for one composition food, read by its Spanish name; none for a food
     * the crosswalk names in no Spanish.
     */
    List<YieldFactorDto> yieldFactorsForFood(Long compositionFoodId);

    /** The household-measure vocabulary the parser reads. */
    List<HouseholdMeasureDto> vocabulary();

    /**
     * Every measure that could weigh a food — narrowed to one unit when given —
     * the diet's own rows first when a diet is given, then the nutritionist's
     * global criteria, then published rows.
     *
     * <p>The food is read as in {@link #rationsForFood}; the diet's and the global
     * criteria naming it are reached by its id.
     */
    List<FoodMeasureDto> measuresForFood(Long compositionFoodId, String unit, Long dietId,
                                         String profileCode);

    /**
     * The measure each written ingredient may be weighed by, in one pass over
     * the published rows, one query for the diet's own criteria and one for the
     * global criteria of the foods asked about — neither when no query names a
     * composition food, since every criterion names one. Precedence: picked, diet,
     * global, published. Answers line up with the queries.
     */
    List<MeasureChoiceDto> chooseMeasures(List<MeasureQueryDto> queries, Long dietId,
                                          String profileCode);

    /**
     * The measure each stored row is weighed by when the rule chooses afresh, each
     * row inside its own diet (FD-054): a criterion written now re-weighs rows of
     * many diets at once. Answers line up with the queries; null where the rule
     * attaches nothing. Three queries whatever the number of rows or diets.
     */
    List<ReferenceFoodMeasure> rechoose(List<ScopedMeasureQueryDto> queries);

    /** Measure rows as managed entities, for a caller that has to point at them. */
    Map<Long, ReferenceFoodMeasure> measureEntities(Collection<Long> ids);

    FoodMeasureDto describe(ReferenceFoodMeasure measure);

    /** One diet's own measures. */
    List<FoodMeasureDto> dietMeasures(Long dietId);

    /**
     * Writes the diet's criterion for one measure of one food (and size),
     * replacing an earlier one for the same measure, food and size, and re-weighs
     * the diet's rows it now reaches whose measure nobody picked. The caller has
     * checked the diet exists.
     */
    MeasureCriterionSavedDto saveDietMeasure(Long dietId, MeasureCriterionRequestDto request);

    void deleteDietMeasure(Long dietId, Long measureId);

    /**
     * Writes a diet's own measures again for another diet, and says which new
     * row stands for which old one, so a copied week keeps weighing the same.
     */
    Map<Long, Long> copyDietMeasures(Long fromDietId, Long toDietId);

    /** Stores a sync's rows: new codes inserted, known codes written over. */
    ReferenceSyncSummaryDto store(ReferenceRowsDto rows);
}
