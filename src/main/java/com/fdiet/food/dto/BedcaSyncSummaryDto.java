package com.fdiet.food.dto;

/**
 * What a sync of the composition database did.
 *
 * <p>{@code source} is the attribution the terms of use require to travel with
 * the data; it is returned here so no caller can store these figures without
 * having been handed it.
 */
public record BedcaSyncSummaryDto(
        int rows,
        int skipped,
        int inserted,
        int updated,
        String source) {
}
