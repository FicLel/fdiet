package com.fdiet.journal.mapper;

import com.fdiet.journal.dto.DishScoreDto;
import com.fdiet.journal.dto.ExtraFoodDto;
import com.fdiet.journal.model.DishScore;
import com.fdiet.journal.model.ExtraFood;

/** Entities to the shapes the web layer sees, and nothing else. */
public interface IJournalMapper {

    DishScoreDto toDto(DishScore score);

    /**
     * The entry with its figures scaled to the quantity logged. The scaling is
     * done here rather than stored, the same way a kcal converted from a
     * kilojoule is.
     */
    ExtraFoodDto toDto(ExtraFood extra);
}
