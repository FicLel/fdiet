package com.fdiet.reference.service;

import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MealShareDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.RecommendationDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.dto.YieldFactorDto;

import java.util.List;
import java.util.Map;

/**
 * The published reference rows as {@link ReferenceService} holds them in
 * memory: transport records only, never entities, read whole once and dropped
 * whenever a sync writes.
 */
record ReferenceSnapshot(
        List<ReferenceSourceDto> sources,
        Map<String, PopulationView> populations,
        List<RationDto> rations,
        List<FoodMeasureDto> measures,
        Map<String, List<RecommendationDto>> recommendations,
        Map<String, List<MealShareDto>> shares,
        Map<String, String> sharePages,
        List<ExchangeSystemDto> exchanges,
        List<YieldFactorDto> yields) {
}
