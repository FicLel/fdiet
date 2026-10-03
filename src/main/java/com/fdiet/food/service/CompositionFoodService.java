package com.fdiet.food.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.CompositionFoodDto;
import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionIndexRow;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.dto.CompositionStoreResultDto;
import com.fdiet.food.exception.CompositionFoodNotFoundException;
import com.fdiet.food.helpers.INameMatcher;
import com.fdiet.food.helpers.NameIndex;
import com.fdiet.food.mapper.ICompositionFoodMapper;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.repository.CompositionFoodRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Owns the {@code composition_foods} table.
 *
 * <p>Names are answered from an in-memory index of every row — about 10,600
 * names and keys, no figures — built with one query on first use and dropped
 * whenever a sync changes the table. A search or a name lookup is then a loop
 * and one {@code findAllById}, never a {@code LIKE '%…%'} per term.
 */
@Service
public class CompositionFoodService implements ICompositionFoodService {

    private static final Sort BY_SOURCE_AND_NAME = Sort.by("source", "nameEn");

    private final CompositionFoodRepository repository;
    private final ICompositionFoodMapper mapper;
    private final INameMatcher nameMatcher;
    private final int batchSize;

    private volatile Index index;

    public CompositionFoodService(CompositionFoodRepository repository,
                                  ICompositionFoodMapper mapper,
                                  INameMatcher nameMatcher,
                                  @Value("${fdiet.composition.batch-size:500}") int batchSize) {
        this.repository = repository;
        this.mapper = mapper;
        this.nameMatcher = nameMatcher;
        this.batchSize = batchSize;
    }

    /** One pass over the index, an O(m log m) sort of the m hits, one {@code findAllById}. */
    @Override
    @Transactional(readOnly = true)
    public PageDto<CompositionFoodDto> search(String name, int page, int size) {
        if (!StringUtils.hasText(name)) {
            return PageDto.of(repository.findAll(PageRequest.of(page, size, BY_SOURCE_AND_NAME)),
                    mapper::toDto);
        }
        Pageable pageable = PageRequest.of(page, size);
        List<Long> ranked = rankedIds(name);
        int from = (int) Math.min(pageable.getOffset(), ranked.size());
        List<Long> pageIds = ranked.subList(from, Math.min(from + size, ranked.size()));
        Map<Long, CompositionFood> foods = entitiesByIds(pageIds);
        List<CompositionFood> content = pageIds.stream().map(foods::get).filter(Objects::nonNull).toList();
        return PageDto.of(new PageImpl<>(content, pageable, ranked.size()), mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public CompositionFoodDto findById(Long id) {
        return mapper.toDto(entityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public CompositionFood entityById(Long id) {
        return repository.findById(id).orElseThrow(() -> new CompositionFoodNotFoundException(id));
    }

    /** O(n) in the names asked for, plus one {@code findAllById} of the ids found. */
    @Override
    @Transactional(readOnly = true)
    public Map<String, CompositionFood> entitiesByName(Collection<String> names) {
        NameIndex<Long> byName = index().names();
        Map<String, Long> wanted = new HashMap<>();
        for (String name : names) {
            String normalised = Texts.normaliseName(name);
            Long id = normalised == null ? null : byName.find(normalised);
            if (id != null) {
                wanted.put(normalised, id);
            }
        }
        Map<Long, CompositionFood> foods = entitiesByIds(new HashSet<>(wanted.values()));
        Map<String, CompositionFood> found = new HashMap<>();
        wanted.forEach((name, id) -> {
            CompositionFood food = foods.get(id);
            if (food != null) {
                found.put(name, food);
            }
        });
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CompositionFood> entitiesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(CompositionFood::getId, Function.identity(), (a, b) -> a));
    }

    /** O(n) in the keys asked for, answered from the index: no query once it is built. */
    @Override
    @Transactional(readOnly = true)
    public Map<CompositionKey, Long> idsByKey(Collection<CompositionKey> keys) {
        Map<CompositionKey, Long> byKey = index().ids();
        Map<CompositionKey, Long> found = new HashMap<>();
        for (CompositionKey key : keys) {
            Long id = byKey.get(key);
            if (id != null) {
                found.put(key, id);
            }
        }
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return repository.count() == 0;
    }

    /**
     * One select of the stored keys, then one batched upsert: two round trips
     * for the select and a few dozen for the batches, whatever the row count.
     * Loading the keys first is what makes "inserted" and "updated" true counts.
     */
    @Override
    @Transactional
    public CompositionStoreResultDto storeAll(List<CompositionFoodRowDto> rows) {
        Set<CompositionKey> stored = repository.findAllIndexRows().stream()
                .map(CompositionIndexRow::key)
                .collect(Collectors.toSet());
        int updated = (int) rows.stream().filter(row -> stored.contains(row.key())).count();
        repository.upsertAll(rows, batchSize);
        dropIndex();
        return new CompositionStoreResultDto(rows.size() - updated, updated);
    }

    /**
     * Drops the index now and again once the sync's transaction ends. Dropping
     * it only inside the transaction would let a search running meanwhile
     * rebuild it from the rows as they were before the commit, and keep those
     * until the next sync.
     */
    private void dropIndex() {
        index = null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    index = null;
                }
            });
        }
    }

    /**
     * Foods sharing a word of the term with their Spanish names come first, most
     * words shared, then the better {@link INameMatcher#score}; then foods that
     * only contain the term as typed, in any of their names. Ties by label.
     */
    private List<Long> rankedIds(String term) {
        Set<String> wanted = nameMatcher.tokens(term);
        String typed = Objects.requireNonNullElse(Texts.key(term), "");
        List<SearchHit> hits = new ArrayList<>();
        for (IndexedFood food : index().foods()) {
            int shared = nameMatcher.shared(wanted, food.tokens());
            if (shared > 0 || food.key().contains(typed)) {
                hits.add(new SearchHit(food.id(), food.label(), shared,
                        nameMatcher.score(wanted, food.tokens())));
            }
        }
        return hits.stream()
                .sorted(Comparator.comparingInt(SearchHit::shared).reversed()
                        .thenComparing(Comparator.comparingInt(SearchHit::score).reversed())
                        .thenComparing(SearchHit::label))
                .map(SearchHit::id)
                .toList();
    }

    private Index index() {
        Index current = index;
        if (current == null) {
            current = indexOf(repository.findAllIndexRows());
            index = current;
        }
        return current;
    }

    /**
     * Every food once, O(n): its Spanish words tokenised for ranking, all its
     * names joined for the as-typed fallback, its Spanish names into the
     * exact-name index, and its {@code (source, source_code)} against its id.
     */
    private Index indexOf(List<CompositionIndexRow> rows) {
        List<IndexedFood> foods = new ArrayList<>(rows.size());
        List<NameIndex.Entry<Long>> named = new ArrayList<>();
        Map<CompositionKey, Long> ids = new HashMap<>(rows.size() * 2);
        for (CompositionIndexRow row : rows) {
            ids.put(row.key(), row.id());
            List<String> spanish = row.nameEs() == null
                    ? List.of()
                    : Stream.concat(Stream.of(row.nameEs()),
                    CompositionLinkDto.splitAliases(row.nameAliases()).stream()).toList();
            if (!spanish.isEmpty()) {
                named.add(new NameIndex.Entry<>(row.id(), spanish, row.namePreferred()));
            }
            String everyName = Stream.concat(spanish.stream(), Stream.of(row.nameEn(), row.nameOriginal()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" | "));
            foods.add(new IndexedFood(row.id(),
                    Objects.requireNonNullElse(row.nameEs(), Objects.requireNonNullElse(row.nameEn(),
                            row.nameOriginal())),
                    Objects.requireNonNullElse(Texts.key(everyName), ""),
                    List.copyOf(nameMatcher.tokens(String.join(" ", spanish)))));
        }
        return new Index(List.copyOf(foods), NameIndex.of(named), Map.copyOf(ids));
    }

    /** The search rows, the exact-name lookup and the stable keys, built together and dropped together. */
    private record Index(List<IndexedFood> foods, NameIndex<Long> names, Map<CompositionKey, Long> ids) {
    }

    /** One food as a search sees it: what to show, every name upper-cased, its Spanish words. */
    private record IndexedFood(Long id, String label, String key, List<String> tokens) {
    }

    private record SearchHit(Long id, String label, int shared, int score) {
    }
}
