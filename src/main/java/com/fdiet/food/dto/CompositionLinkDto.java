package com.fdiet.food.dto;

import com.fdiet.common.helper.Texts;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One row of fdiet's Spanish-name crosswalk
 * ({@code reference-data/composition/composition-es/links.csv}): which CIQUAL or
 * BLS food a Spanish name means, and the edible portion read for it from USDA SR
 * Legacy.
 *
 * <p>{@code reviewed} is false until a person approves the row; a machine
 * prefill never is.
 */
public record CompositionLinkDto(
        CompositionKey key,
        String nameEs,
        List<String> aliases,
        boolean preferred,
        BigDecimal ediblePortion,
        Integer ediblePortionFdcId,
        boolean reviewed) {

    /** How the crosswalk, and the {@code name_aliases} column, separate aliases. */
    public static final String ALIAS_SEPARATOR = ";";

    /** The Spanish name first, then every alias: every way the row can be named. */
    public List<String> names() {
        List<String> names = new ArrayList<>(aliases.size() + 1);
        names.add(nameEs);
        names.addAll(aliases);
        return names;
    }

    /** The aliases of a joined list, trimmed, blanks dropped; none for null. */
    public static List<String> splitAliases(String joined) {
        if (joined == null) {
            return List.of();
        }
        return Arrays.stream(joined.split(ALIAS_SEPARATOR))
                .map(Texts::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    /** The aliases as the column stores them, or null when there are none. */
    public String aliasesJoined() {
        return aliases.isEmpty() ? null : String.join(ALIAS_SEPARATOR, aliases);
    }
}
