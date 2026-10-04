package com.fdiet.journal.mapper;

import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.journal.dto.DishScoreDto;
import com.fdiet.journal.dto.ExtraFoodDto;
import com.fdiet.journal.model.DishScore;
import com.fdiet.journal.model.ExtraFood;
import com.fdiet.journal.service.IJournalNutritionService;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.stereotype.Component;

@Component
public class JournalMapper implements IJournalMapper {

    private final IJournalNutritionService nutritionService;
    private final IReferenceService referenceService;

    public JournalMapper(IJournalNutritionService nutritionService,
                         IReferenceService referenceService) {
        this.nutritionService = nutritionService;
        this.referenceService = referenceService;
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
        CompositionFood food = extra.getCompositionFood();
        FoodItem item = extra.getFoodItem();
        ReferenceFoodMeasure measure = extra.getFoodMeasure();
        return new ExtraFoodDto(
                extra.getId(),
                extra.getDayOfWeek(),
                extra.getRawName(),
                extra.getQuantity(),
                extra.getUnit(),
                extra.getState(),
                extra.getSize(),
                food == null ? null : food.getId(),
                item == null ? null : item.getId(),
                matchedName(food, item),
                food == null ? null : food.getSource(),
                item == null ? null : item.getBrand(),
                measure == null ? null : measure.getId(),
                referenceService.describe(measure),
                nutritionService.of(extra),
                extra.getLoggedAt());
    }

    /** What the catalogue calls the food, or null while it calls it nothing. */
    private static String matchedName(CompositionFood food, FoodItem item) {
        if (food != null) {
            return food.label();
        }
        return item == null ? null : item.getCommercialName();
    }
}
