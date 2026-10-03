package com.fdiet.food.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.BedcaCsvRowDto;
import com.fdiet.food.dto.BedcaFoodDto;
import com.fdiet.food.dto.BedcaNameRow;
import com.fdiet.food.dto.BedcaStoreResultDto;
import com.fdiet.food.dto.FoodSuggestionDto;
import com.fdiet.food.exception.BedcaFoodNotFoundException;
import com.fdiet.food.helpers.INameMatcher;
import com.fdiet.food.mapper.IBedcaFoodMapper;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.repository.BedcaFoodRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the {@code bedca_foods} table.
 */
@Service
public class BedcaFoodService implements IBedcaFoodService {

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name");

    /** How many names go into one {@code IN (…)}. */
    private static final int LOOKUP_CHUNK = 500;

    private final BedcaFoodRepository bedcaFoodRepository;
    private final IBedcaFoodMapper bedcaFoodMapper;
    private final INameMatcher nameMatcher;

    /**
     * Every name in the table, tokenised once. The whole table is under a
     * thousand rows, so this is a few hundred kilobytes that turns a candidate
     * search into a loop instead of a {@code LIKE '%…%'} scan per ingredient.
     * Built on first use and dropped whenever a sync changes the table.
     */
    private volatile List<IndexedFood> index;

    public BedcaFoodService(BedcaFoodRepository bedcaFoodRepository,
                            IBedcaFoodMapper bedcaFoodMapper,
                            INameMatcher nameMatcher) {
        this.bedcaFoodRepository = bedcaFoodRepository;
        this.bedcaFoodMapper = bedcaFoodMapper;
        this.nameMatcher = nameMatcher;
    }

    /**
     * Without a term, the alphabetical page straight from the table. With one,
     * the in-memory index is ranked word by word and only the page asked for is
     * loaded: one {@code findAllById} however many foods match, plus the
     * index's own one-off load.
     */
    @Override
    @Transactional(readOnly = true)
    public PageDto<BedcaFoodDto> search(String name, int page, int size) {
        if (!StringUtils.hasText(name)) {
            return PageDto.of(bedcaFoodRepository.findAll(PageRequest.of(page, size, BY_NAME)),
                    bedcaFoodMapper::toDto);
        }
        // Unsorted: the order is the ranking, not a column.
        Pageable pageable = PageRequest.of(page, size);
        List<Long> ranked = rankedIds(name);
        int from = (int) Math.min(pageable.getOffset(), ranked.size());
        List<Long> pageIds = ranked.subList(from, Math.min(from + size, ranked.size()));
        Map<Long, BedcaFood> foods = entitiesByIds(pageIds);
        List<BedcaFood> content = pageIds.stream()
                .map(foods::get)
                .filter(Objects::nonNull)
                .toList();
        Page<BedcaFood> result = new PageImpl<>(content, pageable, ranked.size());
        return PageDto.of(result, bedcaFoodMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public BedcaFoodDto findById(Long id) {
        return bedcaFoodMapper.toDto(entityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public BedcaFood entityById(Long id) {
        return bedcaFoodRepository.findById(id)
                .orElseThrow(() -> new BedcaFoodNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, BedcaFood> entitiesByName(Collection<String> names) {
        List<String> wanted = names.stream()
                .map(IBedcaFoodService::normalise)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, BedcaFood> found = new HashMap<>();
        for (int from = 0; from < wanted.size(); from += LOOKUP_CHUNK) {
            List<String> chunk = wanted.subList(from, Math.min(from + LOOKUP_CHUNK, wanted.size()));
            for (BedcaFood food : bedcaFoodRepository.findByNameInOrderByIdAsc(chunk)) {
                found.putIfAbsent(IBedcaFoodService.normalise(food.getName()), food);
            }
        }
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BedcaFood> entitiesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bedcaFoodRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(BedcaFood::getId, Function.identity(), (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BedcaFood> entitiesAll() {
        return bedcaFoodRepository.findAll(BY_NAME);
    }

    /**
     * Ranked by how much of each food's name the text accounts for. Foods
     * sharing nothing with it are dropped, and a tie goes to the more specific
     * name — {@code Pollo, pechuga, plancha} ahead of {@code Pollo} when the
     * line mentions both.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FoodSuggestionDto> suggest(String text, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Set<String> wanted = nameMatcher.tokens(text);
        if (wanted.isEmpty()) {
            return List.of();
        }
        return index().stream()
                .map(food -> new FoodSuggestionDto(
                        food.id(), food.name(), food.foodGroup(),
                        nameMatcher.score(wanted, food.tokens())))
                .filter(suggestion -> suggestion.score() > 0)
                .sorted(Comparator.comparingInt(FoodSuggestionDto::score).reversed()
                        .thenComparing(FoodSuggestionDto::name))
                .limit(limit)
                .toList();
    }

    /**
     * Every food the term finds, best first: the more of the term's words a
     * name carries the higher it goes, then the {@link #suggest} order (score,
     * then name). A food sharing no word but containing the term as typed —
     * {@code lechu} inside {@code Lechuga} — still shows, last, so a search box
     * being typed into never goes blank half-way through a word.
     *
     * <p>One pass over the index (957 rows, no query), then an O(m log m) sort
     * of the m foods found.
     */
    private List<Long> rankedIds(String term) {
        Set<String> wanted = nameMatcher.tokens(term);
        String typed = Texts.key(term);
        List<SearchHit> hits = new ArrayList<>();
        for (IndexedFood food : index()) {
            int shared = nameMatcher.shared(wanted, food.tokens());
            if (shared > 0 || food.key().contains(typed)) {
                hits.add(new SearchHit(food.id(), food.name(), shared,
                        nameMatcher.score(wanted, food.tokens())));
            }
        }
        return hits.stream()
                .sorted(Comparator.comparingInt(SearchHit::shared).reversed()
                        .thenComparing(Comparator.comparingInt(SearchHit::score).reversed())
                        .thenComparing(SearchHit::name))
                .map(SearchHit::id)
                .toList();
    }

    /**
     * One select of everything stored, then the rows are written in place or
     * added. Loading the table first is what makes "inserted" and "updated"
     * true counts rather than guesses, and it costs one query for a table this
     * size.
     */
    @Override
    @Transactional
    public BedcaStoreResultDto storeAll(List<BedcaCsvRowDto> rows) {
        Map<Long, BedcaFood> stored = bedcaFoodRepository.findAll().stream()
                .collect(Collectors.toMap(BedcaFood::getId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));

        List<BedcaFood> toInsert = new ArrayList<>();
        int updated = 0;
        for (BedcaCsvRowDto row : rows) {
            BedcaFood food = stored.get(row.id());
            if (food == null) {
                toInsert.add(bedcaFoodMapper.toEntity(row));
            } else {
                bedcaFoodMapper.update(food, row);
                updated++;
            }
        }
        bedcaFoodRepository.saveAll(toInsert);
        index = null;
        return new BedcaStoreResultDto(toInsert.size(), updated);
    }

    private List<IndexedFood> index() {
        List<IndexedFood> current = index;
        if (current == null) {
            current = bedcaFoodRepository.findAllNames().stream()
                    .map(this::indexed)
                    .toList();
            index = current;
        }
        return current;
    }

    /**
     * A name with no word {@link INameMatcher#tokens} keeps ({@code Té}) stays
     * in the index: {@link #suggest} drops it by its zero score, and a search
     * still finds it as typed.
     */
    private IndexedFood indexed(BedcaNameRow row) {
        String key = Objects.requireNonNullElse(Texts.key(row.name()), "");
        return new IndexedFood(row.id(), row.name(), row.foodGroup(), key,
                List.copyOf(nameMatcher.tokens(row.name())));
    }

    /**
     * One row of the suggestion index: its name, the name as a search compares
     * it whole ({@link Texts#key}: upper case, no accents), and the name
     * already tokenised.
     */
    private record IndexedFood(Long id, String name, String foodGroup, String key,
                               List<String> tokens) {
    }

    /** A food a search found, with what it is ranked by. */
    private record SearchHit(Long id, String name, int shared, int score) {
    }
}
