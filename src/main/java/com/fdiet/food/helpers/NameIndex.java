package com.fdiet.food.helpers;

import com.fdiet.common.helper.Texts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact names to the one thing each names: the lookup a diet's ingredient is
 * matched through. Names are compared the way the database collation compares
 * them — case, accents and runs of whitespace ignored ({@link Texts#key}) — and
 * nothing less than equal is a match.
 *
 * <p>A name more than one entry claims belongs to the entry marked preferred.
 * When none or several of them are, the name is a <em>conflict</em>: it answers
 * nothing, and {@link #conflicts()} says so, because guessing between two foods
 * is exactly what matching must never do.
 *
 * <p>Built in O(n) over every name of every entry; a lookup is O(1).
 *
 * @param <K> what an entry is known by (a food id, a crosswalk row's key)
 */
public final class NameIndex<K> {

    /** One thing that can be named, every name it answers to, and whether it wins a shared name. */
    public record Entry<K>(K key, Collection<String> names, boolean preferred) {
    }

    private final Map<String, K> byName;
    private final List<String> conflicts;

    private NameIndex(Map<String, K> byName, List<String> conflicts) {
        this.byName = byName;
        this.conflicts = conflicts;
    }

    public static <K> NameIndex<K> of(Collection<Entry<K>> entries) {
        Map<String, List<Entry<K>>> claims = new LinkedHashMap<>();
        Map<String, String> written = new HashMap<>();
        for (Entry<K> entry : entries) {
            // One entry naming itself twice (a name repeated as an alias) is one claim.
            Set<String> own = new LinkedHashSet<>();
            for (String name : entry.names()) {
                String key = Texts.key(name);
                if (key != null && own.add(key)) {
                    claims.computeIfAbsent(key, k -> new ArrayList<>(1)).add(entry);
                    written.putIfAbsent(key, name.strip());
                }
            }
        }

        Map<String, K> byName = new HashMap<>(claims.size() * 2);
        List<String> conflicts = new ArrayList<>();
        claims.forEach((name, claimants) -> {
            K winner = winnerOf(claimants);
            if (winner == null) {
                conflicts.add(written.get(name));
            } else {
                byName.put(name, winner);
            }
        });
        return new NameIndex<>(byName, List.copyOf(conflicts));
    }

    /** The only claimant, or the one preferred claimant; null when that is not one. */
    private static <K> K winnerOf(List<Entry<K>> claimants) {
        if (claimants.size() == 1) {
            return claimants.get(0).key();
        }
        K winner = null;
        for (Entry<K> claimant : claimants) {
            if (claimant.preferred()) {
                if (winner != null) {
                    return null;
                }
                winner = claimant.key();
            }
        }
        return winner;
    }

    /** What {@code name} names exactly, or null. */
    public K find(String name) {
        String key = Texts.key(name);
        return key == null ? null : byName.get(key);
    }

    /** The names claimed by several entries with not exactly one of them preferred. */
    public List<String> conflicts() {
        return conflicts;
    }
}
