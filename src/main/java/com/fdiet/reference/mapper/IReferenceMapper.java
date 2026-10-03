package com.fdiet.reference.mapper;

import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.YieldFactorDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.RecommendationDto;
import com.fdiet.reference.dto.ReferenceProfileDto;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.model.ReferenceExchangeSystem;
import com.fdiet.reference.model.ReferenceYieldFactor;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.model.ReferenceMealShare;
import com.fdiet.reference.model.ReferencePopulation;
import com.fdiet.reference.model.ReferenceRation;
import com.fdiet.reference.model.ReferenceRecommendation;
import com.fdiet.reference.model.ReferenceSource;

public interface IReferenceMapper {

    ReferenceSourceDto toDto(ReferenceSource source);

    ReferenceProfileDto toDto(ReferencePopulation population, boolean suggested);

    RationDto toDto(ReferenceRation ration);

    FoodMeasureDto toDto(ReferenceFoodMeasure measure);

    RecommendationDto toDto(ReferenceRecommendation recommendation);

    ExchangeSystemDto toDto(ReferenceExchangeSystem system);

    YieldFactorDto toDto(ReferenceYieldFactor factor);

    void update(ReferenceSource source, ReferenceRowsDto.Source row);

    void update(ReferencePopulation population, ReferenceRowsDto.Population row,
                ReferenceSource source);

    /** Writes a ration row; {@code compositionFoodId} is its food's key resolved to this database's id. */
    void update(ReferenceRation ration, ReferenceRowsDto.Ration row,
                ReferencePopulation population, Long compositionFoodId);

    /** Writes a measure row; {@code compositionFoodId} is its food's key resolved to this database's id. */
    void update(ReferenceFoodMeasure measure, ReferenceRowsDto.FoodMeasure row,
                ReferenceSource source, Long compositionFoodId);

    void update(ReferenceRecommendation recommendation, ReferenceRowsDto.Recommendation row,
                ReferencePopulation population);

    void update(ReferenceMealShare share, ReferenceRowsDto.MealShare row,
                ReferencePopulation population);

    void update(ReferenceYieldFactor factor, ReferenceRowsDto.YieldFactor row,
                ReferenceSource source);

    void update(ReferenceExchangeSystem system, ReferenceRowsDto.ExchangeSystem row,
                ReferenceSource source);
}
