package com.fdiet.food.mapper;

import com.fdiet.food.dto.BedcaCsvRowDto;
import com.fdiet.food.dto.BedcaFoodDto;
import com.fdiet.food.model.BedcaFood;

/** Moves a generic food between its stored shape and the shape that crosses a boundary. */
public interface IBedcaFoodMapper {

    BedcaFoodDto toDto(BedcaFood food);

    /** A new entity carrying the row's id — the source assigns it, nothing generates it. */
    BedcaFood toEntity(BedcaCsvRowDto row);

    /** Writes the row over a stored food, so a re-sync brings corrections in. */
    void update(BedcaFood food, BedcaCsvRowDto row);
}
