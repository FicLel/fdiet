package com.fdiet.food.service;

import com.fdiet.common.dto.PageDto;
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

    @Override
    @Transactional(readOnly = true)
    public PageDto<BedcaFoodDto> search(String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, BY_NAME);
        Page<BedcaFood> result = StringUtils.hasText(name)
                ? bedcaFoodRepository.findByNameContaining(name.trim(), pageable)
                : bedcaFoodRepository.findAll(pageable);
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
                    .filter(food -> !food.tokens().isEmpty())
                    .toList();
            index = current;
        }
        return current;
    }

    private IndexedFood indexed(BedcaNameRow row) {
        return new IndexedFood(row.id(), row.name(), row.foodGroup(),
                List.copyOf(nameMatcher.tokens(row.name())));
    }

    /** One row of the suggestion index: its name, already tokenised. */
    private record IndexedFood(Long id, String name, String foodGroup, List<String> tokens) {
    }
}
