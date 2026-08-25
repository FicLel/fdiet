package com.fdiet.food.service;

import com.fdiet.food.dto.CategoryDto;
import com.fdiet.food.mapper.CategoryMapper;
import com.fdiet.food.model.Category;
import com.fdiet.food.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the {@code food_category} table. No other service touches its
 * repository.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findAll() {
        return categoryRepository.findAll().stream().map(categoryMapper::toDto).toList();
    }

    /**
     * Inserts the categories that are not stored yet, keyed by the id the CSV
     * assigns them.
     *
     * @return how many rows were inserted
     */
    @Transactional
    public int storeMissing(Collection<CategoryDto> categories) {
        Map<Long, Category> stored = entitiesById();
        List<Category> missing = categories.stream()
                .filter(dto -> !stored.containsKey(dto.id()))
                .map(categoryMapper::toEntity)
                .toList();
        categoryRepository.saveAll(missing);
        return missing.size();
    }

    /**
     * Every category as a managed entity, keyed by id.
     *
     * <p>Entities, not DTOs, because sibling services need them to build their
     * own associations; this is a service-to-service call, not a layer crossing.
     */
    @Transactional(readOnly = true)
    public Map<Long, Category> entitiesById() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Category::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
