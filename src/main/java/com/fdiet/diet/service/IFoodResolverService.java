package com.fdiet.diet.service;

import java.util.Collection;
import java.util.Map;

/**
 * Matches the words a diet is written in against the catalogue.
 *
 * <p>The whole week is resolved in one call: an imported workbook holds a few
 * hundred ingredients, and asking the catalogue once per ingredient is the N+1
 * this interface exists to prevent.
 *
 * <p>Matching is exact — the columns collate case- and accent-insensitively, so
 * {@code lechuga} finds {@code Lechuga} — and it never guesses. A name that is
 * merely similar to a food is left unmatched and offered as a suggestion
 * instead: {@code 1 pan integral} resembles {@code Pan rallado} closely enough
 * to score well and is not the same food, and a wrong figure in a diet is worse
 * than a blank one.
 */
public interface IFoodResolverService {

    /**
     * The foods for those names, keyed by the name normalised through
     * {@link com.fdiet.food.service.IBedcaFoodService#normalise}.
     *
     * <p>The composition database is asked first, because that is what a diet
     * is written in; the branded catalogue answers only for the names it did
     * not carry. A name neither holds is simply absent — the caller stores that
     * ingredient unmatched rather than dropping it.
     */
    Map<String, FoodMatch> resolve(Collection<String> names);
}
