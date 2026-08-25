package com.fdiet.food.mapper;

import com.fdiet.food.dto.CategoryDto;
import com.fdiet.food.model.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryDto toDto(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryDto(category.getId(), category.getCategory());
    }

    public Category toEntity(CategoryDto dto) {
        return new Category(dto.id(), dto.name());
    }
}
