package com.fdiet.diet.service;

import com.fdiet.common.helper.Texts;
import com.fdiet.diet.helpers.ExactNames;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.food.service.IFoodItemService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves ingredient names against both halves of the catalogue, in four
 * batched queries at most, whatever the size of the week.
 *
 * <p>The composition foods are asked first: CIQUAL 2025 and BLS 4.0, through
 * fdiet's Spanish crosswalk, hold the generic foods a diet is written in, and
 * they are the half that actually answers. They are matched by
 * {@link ExactNames} — exactly, and once more without size words — each pass one
 * {@code findAllById}, the names answered from the composition service's
 * in-memory index. Only the names they do not carry are put to the branded
 * catalogue, where a diet occasionally names a product outright.
 *
 * <p>The branded cache holds <strong>names against ids, never entities</strong>:
 * an entity cached across a transaction is detached, and reattaching it later is
 * exactly the bug this class would otherwise introduce. The ids are turned back
 * into managed entities by {@code entitiesByIds} inside the caller's
 * transaction. It is a plain bounded map in access order rather than a cache
 * manager: one process, one catalogue, and a miss costs one query.
 *
 * <p>The composition half keeps no cache here: its index already is one, and it
 * is dropped on every sync, which a second copy in this class would not be.
 */
@Service
public class FoodResolverService implements IFoodResolverService {

    private final ICompositionFoodService compositionFoodService;
    private final CachedLookup<FoodItem> branded;

    public FoodResolverService(ICompositionFoodService compositionFoodService,
                               IFoodItemService foodItemService,
                               @Value("${fdiet.diet.food-cache-size:5000}") int cacheSize) {
        this.compositionFoodService = compositionFoodService;
        this.branded = new CachedLookup<>(cacheSize,
                foodItemService::entitiesByName, foodItemService::entitiesByIds, FoodItem::getId);
    }

    /** O(n) in the names, two composition lookups and one branded one at most. */
    @Override
    public Map<String, FoodMatch> resolve(Collection<String> names) {
        Map<String, FoodMatch> resolved = new LinkedHashMap<>();
        Map<String, CompositionFood> generic =
                ExactNames.resolve(names, compositionFoodService::entitiesByName);
        generic.forEach((name, food) -> resolved.put(name, FoodMatch.of(food)));

        List<String> rest = names.stream()
                .map(Texts::normaliseName)
                .filter(Objects::nonNull)
                .filter(name -> !resolved.containsKey(name))
                .distinct()
                .toList();
        if (!rest.isEmpty()) {
            branded.resolve(rest).forEach((name, item) -> resolved.put(name, FoodMatch.of(item)));
        }
        return resolved;
    }

    /**
     * The branded catalogue's "name to id", and the two batched calls that turn a
     * set of names into entities.
     */
    private static final class CachedLookup<T> {

        private final Map<String, Long> idsByName;
        private final Function<Collection<String>, Map<String, T>> byName;
        private final Function<Collection<Long>, Map<Long, T>> byIds;
        private final Function<T, Long> idOf;

        private CachedLookup(int maxEntries,
                             Function<Collection<String>, Map<String, T>> byName,
                             Function<Collection<Long>, Map<Long, T>> byIds,
                             Function<T, Long> idOf) {
            this.idsByName = boundedCache(maxEntries);
            this.byName = byName;
            this.byIds = byIds;
            this.idOf = idOf;
        }

        /** @param wanted already normalised and distinct */
        private Map<String, T> resolve(List<String> wanted) {
            Map<String, Long> known = new LinkedHashMap<>();
            List<String> unknown = new ArrayList<>();
            synchronized (idsByName) {
                for (String name : wanted) {
                    Long id = idsByName.get(name);
                    if (id == null) {
                        unknown.add(name);
                    } else {
                        known.put(name, id);
                    }
                }
            }

            Map<String, T> resolved = new LinkedHashMap<>();
            if (!known.isEmpty()) {
                Map<Long, T> found = byIds.apply(known.values());
                known.forEach((name, id) -> {
                    T food = found.get(id);
                    if (food == null) {
                        // Gone from the catalogue; look it up by name again next time.
                        forget(name);
                    } else {
                        resolved.put(name, food);
                    }
                });
            }
            if (!unknown.isEmpty()) {
                Map<String, T> found = byName.apply(unknown);
                resolved.putAll(found);
                remember(found);
            }
            return resolved;
        }

        private void remember(Map<String, T> found) {
            synchronized (idsByName) {
                found.forEach((name, food) -> idsByName.put(name, idOf.apply(food)));
            }
        }

        private void forget(String name) {
            synchronized (idsByName) {
                idsByName.remove(name);
            }
        }

        /** Access-ordered, so what falls out is what has gone longest unused. */
        private static Map<String, Long> boundedCache(int maxEntries) {
            return new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > maxEntries;
                }
            };
        }
    }
}
