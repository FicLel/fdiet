package com.fdiet.food.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.food.dto.FoodCsvRowDto;
import com.fdiet.food.dto.FoodItemDto;
import com.fdiet.food.dto.FoodItemImportResultDto;
import com.fdiet.food.exception.FoodItemNotFoundException;
import com.fdiet.food.mapper.FoodItemMapper;
import com.fdiet.food.model.Category;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.model.SubCategory;
import com.fdiet.food.repository.FoodItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the {@code food_items} table. Categories are reached through their own
 * services, so this class only ever calls {@link FoodItemRepository}.
 */
@Service
public class FoodItemService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

    private final FoodItemRepository foodItemRepository;
    private final FoodItemMapper foodItemMapper;
    private final CategoryService categoryService;
    private final SubCategoryService subCategoryService;
    private final int importBatchSize;

    public FoodItemService(FoodItemRepository foodItemRepository,
                           FoodItemMapper foodItemMapper,
                           CategoryService categoryService,
                           SubCategoryService subCategoryService,
                           @Value("${fdiet.food.import-batch-size:500}") int importBatchSize) {
        this.foodItemRepository = foodItemRepository;
        this.foodItemMapper = foodItemMapper;
        this.categoryService = categoryService;
        this.subCategoryService = subCategoryService;
        this.importBatchSize = importBatchSize;
    }

    /**
     * A page of food items, optionally narrowed to those whose commercial name
     * contains {@code name}.
     */
    @Transactional(readOnly = true)
    public PageDto<FoodItemDto> search(String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);
        Page<FoodItem> result = StringUtils.hasText(name)
                ? foodItemRepository.findByCommercialNameContainingIgnoreCase(name.trim(), pageable)
                : foodItemRepository.findAll(pageable);
        return PageDto.of(result, foodItemMapper::toDto);
    }

    @Transactional(readOnly = true)
    public FoodItemDto findById(Long id) {
        return foodItemRepository.findWithCategoriesById(id)
                .map(foodItemMapper::toDto)
                .orElseThrow(() -> new FoodItemNotFoundException(id));
    }

    /**
     * Stores every row whose EAN is not in the table yet. Existing rows are
     * left untouched, so the import can be run again safely.
     */
    @Transactional
    public FoodItemImportResultDto importRows(List<FoodCsvRowDto> rows) {
        Set<String> knownEans = new HashSet<>(foodItemRepository.findAllEans());
        Map<Long, Category> categories = categoryService.entitiesById();
        Map<Long, SubCategory> subCategories = subCategoryService.entitiesById();

        List<FoodItem> toInsert = new ArrayList<>();
        int alreadyPresent = 0;
        for (FoodCsvRowDto row : rows) {
            // `add` returning false also guards against duplicate EANs inside
            // the same file.
            if (!knownEans.add(row.ean())) {
                alreadyPresent++;
                continue;
            }
            toInsert.add(foodItemMapper.toEntity(
                    row, categories.get(row.categoryId()), subCategories.get(row.subCategoryId())));
        }

        int inserted = foodItemRepository.insertBatch(toInsert, importBatchSize);
        return new FoodItemImportResultDto(inserted, alreadyPresent);
    }
}
