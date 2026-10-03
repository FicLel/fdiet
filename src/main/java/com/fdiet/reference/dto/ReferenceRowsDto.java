package com.fdiet.reference.dto;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.reference.domain.ExchangeNutrient;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.LicenceClass;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.RationRole;
import com.fdiet.reference.domain.RecommendationPeriod;
import com.fdiet.reference.domain.WeightBasis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Every row of the reference CSVs, typed but not yet stored. The importer reads
 * the files into this and hands it to the service that owns the tables, the way
 * {@code BedcaCsvRowDto} works for the composition database.
 *
 * <p>Rows point at each other by code — a ration names its population, a
 * population its source — so the files can be reviewed one at a time and the
 * ids stay the database's business. A row names a composition food the same
 * way, by {@code (source, source_code)}: {@code composition_foods.id} is given
 * by each database, while the source's own code is the same everywhere.
 * {@code origin} is the file and line a row came from, so a row that cannot be
 * stored says where to look.
 */
public record ReferenceRowsDto(
        List<Source> sources,
        List<Population> populations,
        List<Ration> rations,
        List<FoodMeasure> foodMeasures,
        List<Recommendation> recommendations,
        List<MealShare> mealShares,
        List<ExchangeSystem> exchangeSystems,
        List<YieldFactor> yieldFactors) {

    public record Source(String origin, String code, String shortName, String title,
                         String institution, String country, int tier, Integer year, String url,
                         LicenceClass licenceClass, String licence, String attribution,
                         boolean clinical, LocalDate retrievedOn, String notes) {
    }

    public record Population(String origin, String code, String sourceCode, String label,
                             Integer ageMinMonths, Integer ageMaxMonths, String context,
                             String mealSharesFrom, String mealSharesNote, boolean selectable) {
    }

    public record Ration(String origin, String code, String populationCode, String groupCode,
                         String groupLabel, FoodCategory foodCategory, String keywords,
                         CompositionKey compositionFood, String foodLabel, RationRole role,
                         BigDecimal gramsMin, BigDecimal gramsMax, BigDecimal mlMin,
                         BigDecimal mlMax, BigDecimal unitsMin, BigDecimal unitsMax,
                         FoodState state, WeightBasis weightBasis, String householdText,
                         BigDecimal grossGrams, String pageRef, String note) {
    }

    public record FoodMeasure(String origin, String code, String sourceCode,
                              HouseholdMeasure measure, PortionSize size, BigDecimal count,
                              CompositionKey compositionFood, FoodCategory foodCategory,
                              String keywords,
                              String foodLabel, BigDecimal gramsMin, BigDecimal gramsMax,
                              BigDecimal mlMin, BigDecimal mlMax, FoodState state,
                              WeightBasis weightBasis, BigDecimal grossGrams,
                              String householdText, String pageRef, String note) {
    }

    public record Recommendation(String origin, String code, String populationCode, String label,
                                 String groupCodes, BigDecimal rationsMin, BigDecimal rationsMax,
                                 RecommendationPeriod period, String pageRef, String note) {
    }

    public record MealShare(String origin, String code, String populationCode, String mealType,
                            BigDecimal pctMin, BigDecimal pctMax, String pageRef, String note) {
    }

    public record ExchangeSystem(String origin, String code, String sourceCode, String name,
                                 ExchangeNutrient nutrient, BigDecimal gramsPerUnit,
                                 boolean clinical, String note) {
    }

    public record YieldFactor(String origin, String code, String sourceCode,
                              FoodCategory foodCategory, String keywords, String foodLabel,
                              String method, String methodKeywords, BigDecimal yieldPct,
                              Integer samples, String pageRef, String note) {
    }
}
