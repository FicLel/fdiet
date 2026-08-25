package com.fdiet.food.dto;

/** A food subcategory as it crosses a layer boundary. */
public record SubCategoryDto(Long id, String name, Long categoryId) {
}
