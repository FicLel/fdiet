package com.fdiet.food.dto;

/** How many CSV rows turned into new food items, and how many were already stored. */
public record FoodItemImportResultDto(int inserted, int alreadyPresent) {
}
