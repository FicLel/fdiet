package com.fdiet.food.helpers;

import java.util.List;
import java.util.Set;

/**
 * Scores how well the words of a food's name are accounted for by the words a
 * diet used.
 *
 * <p>It ranks; it never decides. See {@link com.fdiet.food.dto.CompositionSuggestionDto}
 * for why the decision stays with a person.
 */
public interface INameMatcher {

    /** The comparable words of a name, without accents, plurals or filler. */
    Set<String> tokens(String text);

    /**
     * How much of {@code foodTokens} the ingredient's words cover, 0–100.
     * Zero when they share nothing, so the caller can drop it.
     */
    int score(Set<String> ingredientTokens, List<String> foodTokens);

    /**
     * How many of {@code foodTokens} appear among the searched words — the
     * count, where {@link #score} is the share. A search ranks by it first, so
     * a food carrying two of the words typed goes above one carrying one.
     */
    int shared(Set<String> searchTokens, List<String> foodTokens);
}
