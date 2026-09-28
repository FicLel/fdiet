package com.fdiet.diet.dto;

import com.fdiet.reference.domain.FoodState;

import java.math.BigDecimal;

/**
 * What an ingredient weighed in one state would weigh in the other, by a
 * published cooking yield — offered beside an ingredient whose text says raw
 * and whose matched food is cooked, or the reverse. <strong>Never applied</strong>:
 * which cut, which method and which source a diet's food stands for is the
 * nutritionist's call, and the figures stay on the quantity written until they
 * rewrite it.
 *
 * <p>{@code writtenGrams} is the quantity as written, in edible grams, in
 * {@code writtenState}; {@code equivalentGrams} is that in {@code foodState}, the
 * state the matched food's figures are published for. Both null when the
 * quantity cannot be weighed. {@code methodNamed} says whether the text names the
 * row's cooking method; false means the source publishes no row for the method
 * written and this is the nearest it has.
 */
public record YieldHintDto(
        FoodState writtenState,
        FoodState foodState,
        BigDecimal writtenGrams,
        BigDecimal equivalentGrams,
        BigDecimal yieldPct,
        String foodLabel,
        String method,
        boolean methodNamed,
        String sourceShortName,
        String pageRef) {
}
