package com.fdiet.food.dto;

import com.fdiet.food.model.CompositionSource;

/**
 * Just enough of a composition food to find it by name and to tell a new row
 * from a stored one. About ten thousand of these fit in memory comfortably, and
 * holding them is what lets a search or a name lookup run without a
 * {@code LIKE '%…%'} scan.
 */
public record CompositionIndexRow(
        Long id,
        CompositionSource source,
        String sourceCode,
        String nameEs,
        String nameAliases,
        boolean namePreferred,
        String nameEn,
        String nameOriginal) {

    public CompositionKey key() {
        return new CompositionKey(source, sourceCode);
    }
}
