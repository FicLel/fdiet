package com.fdiet.diet.service;

import com.fdiet.common.helper.Texts;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.reference.domain.PortionSize;
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
 * Resolves ingredient names against both halves of the catalogue, in six
 * batched queries at most, whatever the size of the week.
 *
 * <p>The composition database is asked first: it holds the generic foods a diet
 * is written in, and it is the half that actually answers. Only the names it
 * does not carry are put to the branded catalogue, where a diet occasionally
 * names a product outright.
 *
 * <p>The caches hold <strong>names against ids, never entities</strong>: an
 * entity cached across a transaction is detached, and reattaching it later is
 * exactly the bug this class would otherwise introduce. The ids are turned back
 * into managed entities by {@code entitiesByIds} inside the caller's
 * transaction.
 *
 * <p>They are plain bounded maps in access order rather than a cache manager:
 * one process, one catalogue, and a miss costs one query.
 */
@Service
public class FoodResolverService implements IFoodResolverService {

    private final CachedLookup<BedcaFood> generic;
    private final CachedLookup<FoodItem> branded;

    public FoodResolverService(IBedcaFoodService bedcaFoodService,
                               IFoodItemService foodItemService,
                               @Value("${fdiet.diet.food-cache-size:5000}") int cacheSize) {
        this.generic = new CachedLookup<>(cacheSize,
                bedcaFoodService::entitiesByName, bedcaFoodService::entitiesByIds, BedcaFood::getId);
        this.branded = new CachedLookup<>(cacheSize,
                foodItemService::entitiesByName, foodItemService::entitiesByIds, FoodItem::getId);
    }

    @Override
    public Map<String, FoodMatch> resolve(Collection<String> names) {
        List<String> wanted = names.stream()
                .map(Texts::normaliseName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }

        Map<String, FoodMatch> resolved = new LinkedHashMap<>();
        generic.resolve(wanted).forEach((name, food) -> resolved.put(name, FoodMatch.of(food)));

        // "kiwi mediano" is a kiwi: a size says how big the piece is, not which
        // food it is. Still an exact match, on the name without that one word.
        Map<String, String> sizeless = new LinkedHashMap<>();
        wanted.stream().filter(name -> !resolved.containsKey(name)).forEach(name -> {
            String stripped = Texts.normaliseName(PortionSize.withoutSize(name));
            if (stripped != null) {
                sizeless.put(name, stripped);
            }
        });
        if (!sizeless.isEmpty()) {
            Map<String, BedcaFood> found = generic.resolve(sizeless.values().stream().distinct().toList());
            sizeless.forEach((name, stripped) -> {
                BedcaFood food = found.get(stripped);
                if (food != null) {
                    resolved.put(name, FoodMatch.of(food));
                }
            });
        }

        List<String> rest = wanted.stream().filter(name -> !resolved.containsKey(name)).toList();
        if (!rest.isEmpty()) {
            branded.resolve(rest).forEach((name, item) -> resolved.put(name, FoodMatch.of(item)));
        }
        return resolved;
    }

    /**
     * One catalogue's worth of "name to id", and the two batched calls that
     * turn a set of names into entities. Both halves work the same way, so they
     * share this rather than each having their own copy of it.
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
