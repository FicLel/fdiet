package com.fdiet.journal.mapper;

import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.journal.dto.DishScoreDto;
import com.fdiet.journal.dto.ExtraFoodDto;
import com.fdiet.journal.model.DishScore;
import com.fdiet.journal.model.ExtraFood;
import com.fdiet.journal.service.IJournalNutritionService;
import org.springframework.stereotype.Component;

@Component
public class JournalMapper implements IJournalMapper {

    private final IJournalNutritionService nutritionService;

    public JournalMapper(IJournalNutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @Override
    public DishScoreDto toDto(DishScore score) {
        return new DishScoreDto(
                score.getDayOfWeek(),
                score.getMealType(),
                score.getDishIndex(),
                score.getScore(),
                score.getScoredAt());
    }

    @Override
    public ExtraFoodDto toDto(ExtraFood extra) {
        BedcaFood bedca = extra.getBedcaFood();
        FoodItem item = extra.getFoodItem();
        return new ExtraFoodDto(
                extra.getId(),
                extra.getDayOfWeek(),
                extra.getRawName(),
                extra.getQuantity(),
                extra.getUnit(),
                bedca == null ? null : bedca.getId(),
                item == null ? null : item.getId(),
                matchedName(bedca, item),
                item == null ? null : item.getBrand(),
                nutritionService.of(extra),
                extra.getLoggedAt());
    }

    /** What the catalogue calls the food, or null while it calls it nothing. */
    private static String matchedName(BedcaFood bedca, FoodItem item) {
        if (bedca != null) {
            return bedca.getName();
        }
        return item == null ? null : item.getCommercialName();
    }
}
