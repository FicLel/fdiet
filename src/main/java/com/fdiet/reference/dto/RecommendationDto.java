package com.fdiet.reference.dto;

import com.fdiet.reference.domain.RecommendationPeriod;

import java.math.BigDecimal;
import java.util.List;

/**
 * How many rations of which groups, how often. Only a maximum is a ceiling
 * ({@code carne: máximo 3 a la semana}), only a minimum a floor.
 */
public record RecommendationDto(
        String code,
        String label,
        List<String> groupCodes,
        BigDecimal rationsMin,
        BigDecimal rationsMax,
        RecommendationPeriod period,
        String pageRef,
        String note) {
}
