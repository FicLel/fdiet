package com.fdiet.diet.helpers;

import com.fdiet.common.helper.Texts;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.KeptMatchDto;
import com.fdiet.diet.exception.InvalidDietException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FD-048: the matches a person already made survive the text being read again.
 * An ingredient whose name is unchanged keeps the food it was matched to — even
 * when its name would match another food, or none — because the person's choice
 * beats the exact-name rule. An edited name is a new ingredient and keeps nothing.
 *
 * <p>Names are compared by {@link Texts#key}, the key the crosswalk's name index
 * matches by: case, accents and runs of whitespace ignored. The same name written
 * twice is paired with the kept matches of that name in order of appearance.
 *
 * <p>Pure and static, like {@link ExactNames}: the measure a kept match carries
 * is only handed on as the ingredient's picked measure, and the measure resolver
 * re-validates it as it does any pick (FD-039) — dropped when the unit written no
 * longer is the word it measures.
 */
public final class KeptMatches {

    private KeptMatches() {
    }

    /**
     * The parsed ingredients, each one a kept match names pointed at that match's
     * food and measure; the rest as parsed. O(n + k) over the ingredients and the
     * kept matches.
     *
     * @throws InvalidDietException when a kept match names both foods or neither
     */
    public static List<DishIngredient> apply(List<DishIngredient> parsed,
                                             Collection<KeptMatchDto> keep) {
        if (keep.isEmpty()) {
            return parsed;
        }
        Map<String, Deque<KeptMatchDto>> byName = indexByName(keep);
        List<DishIngredient> applied = new ArrayList<>(parsed.size());
        for (DishIngredient ingredient : parsed) {
            Deque<KeptMatchDto> waiting = byName.get(Texts.key(ingredient.name()));
            KeptMatchDto kept = waiting == null ? null : waiting.pollFirst();
            applied.add(kept == null
                    ? ingredient
                    : ingredient.matchedTo(kept.compositionFoodId(), kept.foodItemId(),
                    kept.foodMeasureId()));
        }
        return applied;
    }

    private static Map<String, Deque<KeptMatchDto>> indexByName(Collection<KeptMatchDto> keep) {
        Map<String, Deque<KeptMatchDto>> byName = new HashMap<>();
        for (KeptMatchDto kept : keep) {
            if ((kept.compositionFoodId() == null) == (kept.foodItemId() == null)) {
                throw new InvalidDietException("A kept match names one food: send compositionFoodId "
                        + "or foodItemId for " + kept.name() + ", not both and not neither");
            }
            String key = Texts.key(kept.name());
            if (key != null) {
                byName.computeIfAbsent(key, name -> new ArrayDeque<>()).addLast(kept);
            }
        }
        return byName;
    }
}
