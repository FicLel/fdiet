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
 * <p>Matching is exact — case and accents ignored, so {@code lechuga} finds
 * {@code Lechuga}, and once more without size words — and it never guesses
 * ({@link com.fdiet.diet.helpers.ExactNames}). A name that is merely similar to a
 * food is left unmatched and offered as a suggestion instead: {@code 1 pan
 * integral} resembles breadcrumbs closely enough to score well and is not the
 * same food, and a wrong figure in a diet is worse than a blank one.
 */
public interface IFoodResolverService {

    /**
     * The foods for those names, keyed by the name normalised through
     * {@link com.fdiet.common.helper.Texts#normaliseName}.
     *
     * <p>The composition foods (CIQUAL 2025 / BLS 4.0, through fdiet's Spanish
     * crosswalk) are asked first, because that is what a diet is written in;
     * the branded catalogue answers only for the names they did not carry. A name neither holds is simply absent — the caller stores that
     * ingredient unmatched rather than dropping it.
     */
    Map<String, FoodMatch> resolve(Collection<String> names);
}
