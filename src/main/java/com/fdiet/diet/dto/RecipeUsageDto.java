package com.fdiet.diet.dto;

import java.util.List;

/**
 * Where a recipe is on somebody's plate: how many plates serve it, and in whose
 * diets. Asked before a library recipe is edited, since the edit reaches every
 * one of them at once.
 */
public record RecipeUsageDto(Long recipeId, long dishes, List<DietSummaryDto> diets) {
}
