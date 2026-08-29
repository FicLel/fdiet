package com.fdiet.food.service;

import com.fdiet.common.helper.Numbers;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.CategoryDto;
import com.fdiet.food.dto.FoodCsvRowDto;
import com.fdiet.food.dto.FoodItemImportResultDto;
import com.fdiet.food.dto.ImportSummaryDto;
import com.fdiet.food.dto.SubCategoryDto;
import com.fdiet.food.helpers.DataReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns fooddata.csv into rows of the food tables.
 *
 * <p>It owns no repository: it parses the file and hands typed rows to the
 * service that owns each table.
 */
@Service
public class FoodImportService {

    private static final Logger log = LoggerFactory.getLogger(FoodImportService.class);

    // Column positions in fooddata.csv.
    private static final int CATEGORY_ID = 0;
    private static final int CATEGORY_NAME = 1;
    private static final int SUBCATEGORY_ID = 2;
    private static final int SUBCATEGORY_NAME = 3;
    private static final int YEAR = 4;
    private static final int SOURCE = 5;
    private static final int EAN = 6;
    private static final int COMMERCIAL_NAME = 7;
    private static final int BRAND = 8;
    private static final int LEGAL_NAME = 9;
    private static final int INGREDIENTS = 10;
    private static final int ENERGY_KJ = 11;
    private static final int ENERGY_KCAL = 12;
    private static final int FAT = 13;
    private static final int SATURATED_FAT = 14;
    private static final int CARBOHYDRATES = 15;
    private static final int SUGARS = 16;
    private static final int PROTEINS = 17;
    private static final int SALT = 18;
    private static final int COLUMN_COUNT = 19;

    // Widths of the matching varchar columns.
    private static final int NAME_MAX = 500;
    private static final int SHORT_NAME_MAX = 255;
    private static final int EAN_MAX = 32;

    private final DataReader dataReader;
    private final CategoryService categoryService;
    private final SubCategoryService subCategoryService;
    private final IFoodItemService foodItemService;

    public FoodImportService(DataReader dataReader,
                             CategoryService categoryService,
                             SubCategoryService subCategoryService,
                             IFoodItemService foodItemService) {
        this.dataReader = dataReader;
        this.categoryService = categoryService;
        this.subCategoryService = subCategoryService;
        this.foodItemService = foodItemService;
    }

    /**
     * Reads the CSV and stores everything it holds. Running it twice only adds
     * what is missing.
     */
    @Transactional
    public ImportSummaryDto importFromCsv() {
        List<List<String>> records = dataReader.foodData();

        List<FoodCsvRowDto> rows = new ArrayList<>(records.size());
        int skipped = 0;
        for (List<String> record : records) {
            FoodCsvRowDto row = toRow(record);
            if (row == null) {
                skipped++;
                continue;
            }
            rows.add(row);
        }
        log.info("Parsed {} food rows from the CSV ({} skipped)", rows.size(), skipped);

        int categoriesStored = categoryService.storeMissing(distinctCategories(rows));
        int subCategoriesStored = subCategoryService.storeMissing(distinctSubCategories(rows));
        FoodItemImportResultDto items = foodItemService.importRows(rows);
        log.info("Imported {} food items ({} already present)", items.inserted(), items.alreadyPresent());

        return new ImportSummaryDto(
                records.size(),
                skipped,
                categoriesStored,
                subCategoriesStored,
                items.inserted(),
                items.alreadyPresent()
        );
    }

    /** Returns null for the header line and for anything that is not a usable row. */
    private FoodCsvRowDto toRow(List<String> record) {
        if (record.size() < COLUMN_COUNT) {
            return null;
        }
        Long categoryId = toLong(record.get(CATEGORY_ID));
        Long subCategoryId = toLong(record.get(SUBCATEGORY_ID));
        String ean = text(record.get(EAN), EAN_MAX);
        if (categoryId == null || subCategoryId == null || ean == null) {
            return null;
        }
        return new FoodCsvRowDto(
                categoryId,
                text(record.get(CATEGORY_NAME), SHORT_NAME_MAX),
                subCategoryId,
                text(record.get(SUBCATEGORY_NAME), SHORT_NAME_MAX),
                toInteger(record.get(YEAR)),
                text(record.get(SOURCE), SHORT_NAME_MAX),
                ean,
                text(record.get(COMMERCIAL_NAME), NAME_MAX),
                text(record.get(BRAND), SHORT_NAME_MAX),
                text(record.get(LEGAL_NAME), NAME_MAX),
                text(record.get(INGREDIENTS), Integer.MAX_VALUE),
                toDecimal(record.get(ENERGY_KJ)),
                toDecimal(record.get(ENERGY_KCAL)),
                toDecimal(record.get(FAT)),
                toDecimal(record.get(SATURATED_FAT)),
                toDecimal(record.get(CARBOHYDRATES)),
                toDecimal(record.get(SUGARS)),
                toDecimal(record.get(PROTEINS)),
                toDecimal(record.get(SALT))
        );
    }

    private List<CategoryDto> distinctCategories(List<FoodCsvRowDto> rows) {
        Map<Long, CategoryDto> byId = new LinkedHashMap<>();
        for (FoodCsvRowDto row : rows) {
            byId.putIfAbsent(row.categoryId(), new CategoryDto(row.categoryId(), row.categoryName()));
        }
        return List.copyOf(byId.values());
    }

    private List<SubCategoryDto> distinctSubCategories(List<FoodCsvRowDto> rows) {
        Map<Long, SubCategoryDto> byId = new LinkedHashMap<>();
        for (FoodCsvRowDto row : rows) {
            byId.putIfAbsent(row.subCategoryId(),
                    new SubCategoryDto(row.subCategoryId(), row.subCategoryName(), row.categoryId()));
        }
        return List.copyOf(byId.values());
    }

    private static String text(String value, int maxLength) {
        return Texts.clean(value, maxLength);
    }

    private static Long toLong(String value) {
        return Numbers.toLong(value);
    }

    private static Integer toInteger(String value) {
        return Numbers.toInteger(value);
    }

    private static BigDecimal toDecimal(String value) {
        return Numbers.toDecimal(value);
    }
}
