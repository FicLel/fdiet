package com.fdiet.food.mapper;

import com.fdiet.food.dto.SubCategoryDto;
import com.fdiet.food.model.Category;
import com.fdiet.food.model.SubCategory;
import org.springframework.stereotype.Component;

@Component
public class SubCategoryMapper {

    public SubCategoryDto toDto(SubCategory subCategory) {
        if (subCategory == null) {
            return null;
        }
        // Reading the id off a lazy association does not initialise the proxy.
        return new SubCategoryDto(
                subCategory.getId(),
                subCategory.getSubcategory(),
                subCategory.getCategory().getId()
        );
    }

    public SubCategory toEntity(SubCategoryDto dto, Category category) {
        return new SubCategory(dto.id(), dto.name(), category);
    }
}
