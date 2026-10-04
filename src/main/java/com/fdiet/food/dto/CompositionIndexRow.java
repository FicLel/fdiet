package com.fdiet.food.dto;

import com.fdiet.food.helpers.NameIndex;
import com.fdiet.food.model.CompositionSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

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

    /** The Spanish name and every alias the crosswalk gives the food; empty when it names none. */
    public List<String> spanishNames() {
        if (nameEs == null) {
            return List.of();
        }
        return Stream.concat(Stream.of(nameEs), CompositionLinkDto.splitAliases(nameAliases).stream())
                .toList();
    }

    /**
     * The exact-name lookup a diet's ingredient is matched through: every Spanish
     * name and alias against its food's id, a name two foods share going to the
     * preferred one. The one rule, used by the food service and by the migration
     * that re-matched the stored weeks (V18), so the two can never disagree.
     * O(n) over the rows.
     */
    public static NameIndex<Long> nameIndex(Collection<CompositionIndexRow> rows) {
        List<NameIndex.Entry<Long>> named = new ArrayList<>();
        for (CompositionIndexRow row : rows) {
            List<String> spanish = row.spanishNames();
            if (!spanish.isEmpty()) {
                named.add(new NameIndex.Entry<>(row.id(), spanish, row.namePreferred()));
            }
        }
        return NameIndex.of(named);
    }
}
