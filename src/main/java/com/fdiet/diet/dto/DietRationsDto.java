package com.fdiet.diet.dto;

import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.MealSharesDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.domain.RecommendationPeriod;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

/**
 * A week read against a reference profile: how many rations of each group it
 * holds, how that compares with how often the profile recommends them, how the
 * day's energy is shared between its meals, and — on a clinical diet — how many
 * carbohydrate rations each meal carries.
 *
 * <p><strong>Orientative, and derived on every read.</strong> Nothing here is
 * stored. A ration defined as a range divides into a range ({@code 70 g ÷ 60-80 g
 * = 0,88-1,17}) and is sent as one; nothing picks a midpoint. Every day travels
 * with the counts of what could not be counted and why, the way every total in
 * this codebase travels with its counts: an ingredient not matched, not
 * weighable, in no ration group, or weighed in another state than the ration is
 * defined in (cooked lentils against a dry ration) is named, never silently left
 * out.
 *
 * <p>{@code profile} is null when the diet has none; the meal energy shares are
 * then drawn without a target and no ration is counted.
 */
public record DietRationsDto(
        Long dietId,
        Profile profile,
        MealSharesDto mealShares,
        List<ExchangeSystemDto> exchangeSystems,
        List<DayRations> days,
        List<RecommendationCheck> weekly,
        int daysInWeek,
        List<ReferenceSourceDto> sources) {

    /** The profile the week was counted against. */
    public record Profile(String code, String label, String sourceCode, String sourceShortName,
                          String context) {
    }

    /** One day of the week, counted. */
    public record DayRations(
            DayOfWeek day,
            List<GroupCount> groups,
            List<RecommendationCheck> daily,
            List<MealEnergy> meals,
            List<ExchangeCount> exchanges,
            Coverage coverage,
            List<Uncounted> uncounted) {
    }

    /**
     * The grams of one ration group the day holds, as a number of that group's
     * rations — a range when the ration is.
     */
    public record GroupCount(String groupCode, String groupLabel, BigDecimal grams,
                             BigDecimal rationsMin, BigDecimal rationsMax, int ingredients) {
    }

    /**
     * One recommendation against what the day (or the week) holds.
     *
     * <p>{@code status}: {@code WITHIN} when the whole counted range sits inside
     * the recommendation, {@code BELOW}/{@code ABOVE} when all of it falls
     * outside, {@code UNCERTAIN} when the range straddles a bound.
     *
     * <p>While the period holds an ingredient that could belong to a group but
     * was not counted (unmatched, unweighed, weighed in another state), the count
     * is only a floor: {@code BELOW}, and {@code WITHIN} against a ceiling, become
     * {@code UNCERTAIN}. {@code ABOVE} stays — what was counted already exceeds.
     */
    public record RecommendationCheck(String code, String label, List<String> groupCodes,
                                      BigDecimal rationsMin, BigDecimal rationsMax,
                                      RecommendationPeriod period, BigDecimal actualMin,
                                      BigDecimal actualMax, Status status, String note,
                                      String pageRef) {
    }

    public enum Status {
        WITHIN,
        BELOW,
        ABOVE,
        UNCERTAIN
    }

    /**
     * A meal slot's energy and its share of the day, beside the share the
     * profile's distribution gives it (null without one).
     */
    public record MealEnergy(MealType mealType, String name, BigDecimal kcal, BigDecimal pct,
                             BigDecimal targetPctMin, BigDecimal targetPctMax,
                             BigDecimal carbohydratesG) {
    }

    /** An exchange system's count for the day and for each meal of it. */
    public record ExchangeCount(String code, String name, BigDecimal gramsPerUnit,
                                BigDecimal dayUnits, List<MealUnits> meals) {
    }

    public record MealUnits(MealType mealType, BigDecimal units) {
    }

    /**
     * Every ingredient of the day in exactly one bucket:
     * {@code counted + unmatched + unweighed + noRation + stateMismatch == ingredients}.
     */
    public record Coverage(int ingredients, int counted, int unmatched, int unweighed,
                           int noRation, int stateMismatch) {
    }

    /** An ingredient left out of the ration count, and why. */
    public record Uncounted(String name, String reason) {
    }
}
