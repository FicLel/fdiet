package com.fdiet.food.dto;

import com.fdiet.food.model.CompositionSource;

/**
 * What a composition food is known by across syncs: its table and that table's
 * own code. {@code uk_composition_foods_source_code} holds the same pair.
 */
public record CompositionKey(CompositionSource source, String sourceCode) {

    @Override
    public String toString() {
        return source + " " + sourceCode;
    }
}
