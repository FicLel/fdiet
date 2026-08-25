package com.fdiet.food.mapper;

import com.fdiet.food.dto.FoodCsvRowDto;
import com.fdiet.food.dto.FoodItemDto;
import com.fdiet.food.model.Category;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.model.SubCategory;
import org.springframework.stereotype.Component;

@Component
public class FoodItemMapper {

    private final CategoryMapper categoryMapper;
    private final SubCategoryMapper subCategoryMapper;

    public FoodItemMapper(CategoryMapper categoryMapper, SubCategoryMapper subCategoryMapper) {
        this.categoryMapper = categoryMapper;
        this.subCategoryMapper = subCategoryMapper;
    }

    public FoodItemDto toDto(FoodItem item) {
        return new FoodItemDto(
                item.getId(),
                categoryMapper.toDto(item.getCategory()),
                subCategoryMapper.toDto(item.getSubcategory()),
                item.getYear(),
                item.getSourceName(),
                item.getMarketShareTotalEan(),
                item.getEan(),
                item.getCommercialName(),
                item.getManufacturer(),
                item.getBrand(),
                item.getSubbrand(),
                item.getLegalName(),
                item.getIngredients(),
                item.getPortionSizeG(),
                item.getEnergyKj(),
                item.getEnergyKcal(),
                item.getFatG(),
                item.getSaturatedFatG(),
                item.getCarbohydratesG(),
                item.getSugarsG(),
                item.getProteinsG(),
                item.getSaltG(),
                item.getSodiumG(),
                item.getMonounsaturatedFatG(),
                item.getPolyunsaturatedFatG(),
                item.getStarchG(),
                item.getFiberG(),
                item.getPolyolsG(),
                item.getSweeteners()
        );
    }

    /** Builds a new item from a CSV row; the associations are resolved by the caller. */
    public FoodItem toEntity(FoodCsvRowDto row, Category category, SubCategory subCategory) {
        FoodItem item = new FoodItem();
        item.setCategory(category);
        item.setSubcategory(subCategory);
        item.setYear(row.year());
        item.setSourceName(row.sourceName());
        item.setEan(row.ean());
        item.setCommercialName(row.commercialName());
        item.setBrand(row.brand());
        item.setLegalName(row.legalName());
        item.setIngredients(row.ingredients());
        item.setEnergyKj(row.energyKj());
        item.setEnergyKcal(row.energyKcal());
        item.setFatG(row.fatG());
        item.setSaturatedFatG(row.saturatedFatG());
        item.setCarbohydratesG(row.carbohydratesG());
        item.setSugarsG(row.sugarsG());
        item.setProteinsG(row.proteinsG());
        item.setSaltG(row.saltG());
        return item;
    }
}
