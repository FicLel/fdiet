package com.fdiet.food.mapper;

import com.fdiet.food.dto.CompositionFoodDto;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.dto.NutrientDto;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.Nutrient;
import com.fdiet.food.model.NutrientValue;
import com.fdiet.food.service.INutritionService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CompositionFoodMapper implements ICompositionFoodMapper {

    private final INutritionService nutritionService;

    public CompositionFoodMapper(INutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    /**
     * The published figures go out verbatim with their units, the converted
     * ones beside them, and the source and its attribution with both. A
     * component the source left blank is absent rather than zero.
     */
    @Override
    public CompositionFoodDto toDto(CompositionFood food) {
        Map<String, NutrientDto> nutrients = new LinkedHashMap<>();
        for (Nutrient nutrient : Nutrient.values()) {
            NutrientValue value = nutrient.of(food);
            if (value != null && value.getValue() != null) {
                nutrients.put(nutrient.key(), new NutrientDto(value.getValue(), value.getUnit()));
            }
        }
        return new CompositionFoodDto(
                food.getId(),
                food.getSource(),
                food.getSource().label(),
                food.getSource().attribution(),
                food.getSourceCode(),
                food.getNameOriginal(),
                food.getNameEn(),
                food.getFoodGroupCode(),
                food.getNameEs(),
                CompositionLinkDto.splitAliases(food.getNameAliases()),
                food.isNamePreferred(),
                food.isNameReviewed(),
                food.getEdiblePortion(),
                food.getEdiblePortionFdcId(),
                nutrients.containsKey(Nutrient.ENERGY.key()),
                nutrients,
                nutritionService.per100g(food));
    }
}
