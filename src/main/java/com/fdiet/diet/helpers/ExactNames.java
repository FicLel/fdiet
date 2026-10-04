package com.fdiet.diet.helpers;

import com.fdiet.common.helper.Texts;
import com.fdiet.reference.domain.PortionSize;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The rule an ingredient's name is matched to a composition food by: exactly, and
 * once more without its size words — {@code kiwi mediano} is a kiwi, since a size
 * says how big the piece is and never which food it is. Nothing less than equal is
 * a match; anything else is left for a person, with suggestions.
 *
 * <p>Pure and static on purpose: the resolver a fresh import goes through and the
 * migration that re-matched the stored weeks (V18), which runs before any bean
 * exists, apply this one copy of the rule, so the two cannot drift.
 */
public final class ExactNames {

    private ExactNames() {
    }

    /**
     * What each name matches, keyed by the name normalised through
     * {@link Texts#normaliseName}. A name nothing matches is absent.
     *
     * <p>{@code lookup} is asked at most twice — the names as written, then the
     * sizeless ones that missed — so a batched lookup stays two calls whatever the
     * number of names. O(n) around it.
     *
     * @param lookup answers names already normalised, keyed by those same names
     */
    public static <T> Map<String, T> resolve(Collection<String> names,
                                             Function<Collection<String>, Map<String, T>> lookup) {
        List<String> wanted = names.stream()
                .map(Texts::normaliseName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<String, T> resolved = new LinkedHashMap<>(lookup.apply(wanted));

        Map<String, String> sizeless = new LinkedHashMap<>();
        for (String name : wanted) {
            String stripped = resolved.containsKey(name)
                    ? null
                    : Texts.normaliseName(PortionSize.withoutSize(name));
            if (stripped != null) {
                sizeless.put(name, stripped);
            }
        }
        if (sizeless.isEmpty()) {
            return resolved;
        }
        Map<String, T> found = lookup.apply(sizeless.values().stream().distinct().toList());
        sizeless.forEach((name, stripped) -> {
            T match = found.get(stripped);
            if (match != null) {
                resolved.put(name, match);
            }
        });
        return resolved;
    }
}
