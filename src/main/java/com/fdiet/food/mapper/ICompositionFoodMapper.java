package com.fdiet.food.mapper;

import com.fdiet.food.dto.CompositionFoodDto;
import com.fdiet.food.model.CompositionFood;

/** Moves a CIQUAL or BLS food from its stored shape to the shape that crosses a boundary. */
public interface ICompositionFoodMapper {

    CompositionFoodDto toDto(CompositionFood food);
}
