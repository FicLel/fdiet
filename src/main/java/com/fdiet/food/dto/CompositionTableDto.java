package com.fdiet.food.dto;

import java.util.List;

/**
 * What a reader made of one source's file: the rows it could key, and how many
 * it could not (no code, or no name).
 */
public record CompositionTableDto(List<CompositionFoodRowDto> rows, int skipped) {
}
