package com.fdiet.food.mapper;

import com.fdiet.food.dto.BedcaCsvRowDto;
import com.fdiet.food.dto.BedcaFoodDto;
import com.fdiet.food.dto.NutrientDto;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.Nutrient;
import com.fdiet.food.model.NutrientValue;
import com.fdiet.food.service.INutritionService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BedcaFoodMapper implements IBedcaFoodMapper {

    private final INutritionService nutritionService;

    public BedcaFoodMapper(INutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    /**
     * The published figures go out verbatim, each with its own unit, and the
     * converted ones go out beside them. A component the source left empty is
     * left out of the map rather than sent as zero.
     */
    @Override
    public BedcaFoodDto toDto(BedcaFood food) {
        Map<String, NutrientDto> nutrients = new LinkedHashMap<>();
        for (Nutrient nutrient : Nutrient.values()) {
            NutrientValue value = nutrient.of(food);
            if (value != null && value.getValue() != null) {
                nutrients.put(nutrient.key(), new NutrientDto(value.getValue(), value.getUnit()));
            }
        }
        return new BedcaFoodDto(
                food.getId(),
                food.getName(),
                food.getEnglishName(),
                food.getScientificName(),
                food.getFoodGroup(),
                food.getFoodSubgroup(),
                food.getOrigin(),
                food.getEdiblePortion(),
                nutrients,
                nutritionService.per100g(food));
    }

    @Override
    public BedcaFood toEntity(BedcaCsvRowDto row) {
        BedcaFood food = new BedcaFood();
        food.setId(row.id());
        update(food, row);
        return food;
    }

    @Override
    public void update(BedcaFood food, BedcaCsvRowDto row) {
        food.setName(row.name());
        food.setEnglishName(row.englishName());
        food.setScientificName(row.scientificName());
        food.setFoodGroup(row.foodGroup());
        food.setFoodSubgroup(row.foodSubgroup());
        food.setOrigin(row.origin());
        food.setEdiblePortion(row.ediblePortion());
        for (Nutrient nutrient : Nutrient.values()) {
            NutrientDto published = row.nutrients().get(nutrient.key());
            nutrient.set(food, published == null
                    ? null
                    : NutrientValue.of(published.value(), published.unit()));
        }
    }
}
