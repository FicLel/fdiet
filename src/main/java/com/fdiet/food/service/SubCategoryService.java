package com.fdiet.food.service;

import com.fdiet.food.dto.SubCategoryDto;
import com.fdiet.food.mapper.SubCategoryMapper;
import com.fdiet.food.model.Category;
import com.fdiet.food.model.SubCategory;
import com.fdiet.food.repository.SubCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the {@code food_subcategory} table. It reaches categories through
 * {@link CategoryService}, never through the category repository.
 */
@Service
public class SubCategoryService {

    private final SubCategoryRepository subCategoryRepository;
    private final SubCategoryMapper subCategoryMapper;
    private final CategoryService categoryService;

    public SubCategoryService(SubCategoryRepository subCategoryRepository,
                              SubCategoryMapper subCategoryMapper,
                              CategoryService categoryService) {
        this.subCategoryRepository = subCategoryRepository;
        this.subCategoryMapper = subCategoryMapper;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public List<SubCategoryDto> findAll() {
        return subCategoryRepository.findAll().stream().map(subCategoryMapper::toDto).toList();
    }

    /**
     * Inserts the subcategories that are not stored yet. Rows whose category is
     * unknown are ignored, so the caller has to store categories first.
     *
     * @return how many rows were inserted
     */
    @Transactional
    public int storeMissing(Collection<SubCategoryDto> subCategories) {
        Map<Long, SubCategory> stored = entitiesById();
        Map<Long, Category> categories = categoryService.entitiesById();

        List<SubCategory> missing = new ArrayList<>();
        for (SubCategoryDto dto : subCategories) {
            if (stored.containsKey(dto.id())) {
                continue;
            }
            Category category = categories.get(dto.categoryId());
            if (category == null) {
                continue;
            }
            missing.add(subCategoryMapper.toEntity(dto, category));
        }
        subCategoryRepository.saveAll(missing);
        return missing.size();
    }

    /** Every subcategory as a managed entity, keyed by id. See {@link CategoryService#entitiesById()}. */
    @Transactional(readOnly = true)
    public Map<Long, SubCategory> entitiesById() {
        return subCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(
                        SubCategory::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
