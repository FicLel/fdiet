package com.fdiet.reference.mapper;

import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.RecommendationDto;
import com.fdiet.reference.dto.ReferenceProfileDto;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.model.ReferenceExchangeSystem;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.model.ReferenceMealShare;
import com.fdiet.reference.model.ReferencePopulation;
import com.fdiet.reference.model.ReferenceRation;
import com.fdiet.reference.model.ReferenceRecommendation;
import com.fdiet.reference.model.ReferenceSource;
import org.springframework.stereotype.Component;

@Component
public class ReferenceMapper implements IReferenceMapper {

    @Override
    public ReferenceSourceDto toDto(ReferenceSource source) {
        return new ReferenceSourceDto(
                source.getCode(),
                source.getShortName(),
                source.getTitle(),
                source.getInstitution(),
                source.getCountry(),
                source.getTier(),
                source.getYear(),
                source.getUrl(),
                source.getLicenceClass(),
                source.getLicence(),
                source.getAttribution(),
                source.isClinical(),
                source.getRetrievedOn(),
                source.getNotes());
    }

    @Override
    public ReferenceProfileDto toDto(ReferencePopulation population, boolean suggested) {
        return new ReferenceProfileDto(
                population.getCode(),
                population.getLabel(),
                population.getSource().getCode(),
                population.getSource().getShortName(),
                population.getAgeMinMonths(),
                population.getAgeMaxMonths(),
                population.getContext(),
                population.isSelectable(),
                suggested);
    }

    @Override
    public RationDto toDto(ReferenceRation ration) {
        ReferencePopulation population = ration.getPopulation();
        return new RationDto(
                ration.getId(),
                ration.getCode(),
                population.getCode(),
                population.getLabel(),
                population.getSource().getCode(),
                population.getSource().getShortName(),
                ration.getGroupCode(),
                ration.getGroupLabel(),
                ration.getFoodCategory(),
                ration.getKeywords(),
                ration.getFoodLabel(),
                ration.getBedcaFoodId(),
                ration.getRole(),
                ration.getGramsMin(),
                ration.getGramsMax(),
                ration.getMlMin(),
                ration.getMlMax(),
                ration.getUnitsMin(),
                ration.getUnitsMax(),
                ration.getState(),
                ration.getWeightBasis(),
                ration.getHouseholdText(),
                ration.getGrossGrams(),
                ration.getPageRef(),
                ration.getNote());
    }

    /** A diet's own row carries no source: it is shown as the professional's criterion. */
    @Override
    public FoodMeasureDto toDto(ReferenceFoodMeasure measure) {
        ReferenceSource source = measure.getSource();
        return new FoodMeasureDto(
                measure.getId(),
                measure.getCode(),
                measure.getMeasure(),
                measure.getMeasure().label(),
                measure.getSize(),
                measure.getCount(),
                measure.getFoodLabel(),
                measure.getBedcaFoodId(),
                measure.getFoodCategory(),
                measure.getKeywords(),
                measure.getGramsMin(),
                measure.getGramsMax(),
                measure.getMlMin(),
                measure.getMlMax(),
                measure.gramsPerMeasure(),
                measure.getState(),
                measure.getWeightBasis(),
                measure.getGrossGrams(),
                measure.getHouseholdText(),
                measure.getPageRef(),
                measure.getNote(),
                source == null ? null : source.getCode(),
                source == null ? null : source.getShortName(),
                source == null ? null : source.getTier(),
                measure.getDietId(),
                measure.isDietOwn());
    }

    @Override
    public RecommendationDto toDto(ReferenceRecommendation recommendation) {
        return new RecommendationDto(
                recommendation.getCode(),
                recommendation.getLabel(),
                recommendation.groups(),
                recommendation.getRationsMin(),
                recommendation.getRationsMax(),
                recommendation.getPeriod(),
                recommendation.getPageRef(),
                recommendation.getNote());
    }

    @Override
    public ExchangeSystemDto toDto(ReferenceExchangeSystem system) {
        return new ExchangeSystemDto(
                system.getCode(),
                system.getName(),
                system.getNutrient(),
                system.getGramsPerUnit(),
                system.isClinical(),
                system.getSource().getCode(),
                system.getSource().getShortName(),
                system.getNote());
    }

    @Override
    public void update(ReferenceSource source, ReferenceRowsDto.Source row) {
        source.setCode(row.code());
        source.setShortName(row.shortName());
        source.setTitle(row.title());
        source.setInstitution(row.institution());
        source.setCountry(row.country());
        source.setTier(row.tier());
        source.setYear(row.year());
        source.setUrl(row.url());
        source.setLicenceClass(row.licenceClass());
        source.setLicence(row.licence());
        source.setAttribution(row.attribution());
        source.setClinical(row.clinical());
        source.setRetrievedOn(row.retrievedOn());
        source.setNotes(row.notes());
    }

    @Override
    public void update(ReferencePopulation population, ReferenceRowsDto.Population row,
                       ReferenceSource source) {
        population.setCode(row.code());
        population.setSource(source);
        population.setLabel(row.label());
        population.setAgeMinMonths(row.ageMinMonths());
        population.setAgeMaxMonths(row.ageMaxMonths());
        population.setContext(row.context());
        population.setMealSharesFrom(row.mealSharesFrom());
        population.setMealSharesNote(row.mealSharesNote());
        population.setSelectable(row.selectable());
    }

    @Override
    public void update(ReferenceRation ration, ReferenceRowsDto.Ration row,
                       ReferencePopulation population) {
        ration.setCode(row.code());
        ration.setPopulation(population);
        ration.setGroupCode(row.groupCode());
        ration.setGroupLabel(row.groupLabel());
        ration.setFoodCategory(row.foodCategory());
        ration.setKeywords(row.keywords());
        ration.setBedcaFoodId(row.bedcaFoodId());
        ration.setFoodLabel(row.foodLabel());
        ration.setRole(row.role());
        ration.setGramsMin(row.gramsMin());
        ration.setGramsMax(row.gramsMax());
        ration.setMlMin(row.mlMin());
        ration.setMlMax(row.mlMax());
        ration.setUnitsMin(row.unitsMin());
        ration.setUnitsMax(row.unitsMax());
        ration.setState(row.state());
        ration.setWeightBasis(row.weightBasis());
        ration.setHouseholdText(row.householdText());
        ration.setGrossGrams(row.grossGrams());
        ration.setPageRef(row.pageRef());
        ration.setNote(row.note());
    }

    @Override
    public void update(ReferenceFoodMeasure measure, ReferenceRowsDto.FoodMeasure row,
                       ReferenceSource source) {
        measure.setCode(row.code());
        measure.setSource(source);
        measure.setDietId(null);
        measure.setMeasure(row.measure());
        measure.setSize(row.size());
        measure.setCount(row.count());
        measure.setBedcaFoodId(row.bedcaFoodId());
        measure.setFoodCategory(row.foodCategory());
        measure.setKeywords(row.keywords());
        measure.setFoodLabel(row.foodLabel());
        measure.setGramsMin(row.gramsMin());
        measure.setGramsMax(row.gramsMax());
        measure.setMlMin(row.mlMin());
        measure.setMlMax(row.mlMax());
        measure.setState(row.state());
        measure.setWeightBasis(row.weightBasis());
        measure.setGrossGrams(row.grossGrams());
        measure.setHouseholdText(row.householdText());
        measure.setPageRef(row.pageRef());
        measure.setNote(row.note());
    }

    @Override
    public void update(ReferenceRecommendation recommendation, ReferenceRowsDto.Recommendation row,
                       ReferencePopulation population) {
        recommendation.setCode(row.code());
        recommendation.setPopulation(population);
        recommendation.setLabel(row.label());
        recommendation.setGroupCodes(row.groupCodes());
        recommendation.setRationsMin(row.rationsMin());
        recommendation.setRationsMax(row.rationsMax());
        recommendation.setPeriod(row.period());
        recommendation.setPageRef(row.pageRef());
        recommendation.setNote(row.note());
    }

    @Override
    public void update(ReferenceMealShare share, ReferenceRowsDto.MealShare row,
                       ReferencePopulation population) {
        share.setCode(row.code());
        share.setPopulation(population);
        share.setMealType(row.mealType());
        share.setPctMin(row.pctMin());
        share.setPctMax(row.pctMax());
        share.setPageRef(row.pageRef());
        share.setNote(row.note());
    }

    @Override
    public void update(ReferenceExchangeSystem system, ReferenceRowsDto.ExchangeSystem row,
                       ReferenceSource source) {
        system.setCode(row.code());
        system.setSource(source);
        system.setName(row.name());
        system.setNutrient(row.nutrient());
        system.setGramsPerUnit(row.gramsPerUnit());
        system.setClinical(row.clinical());
        system.setNote(row.note());
    }
}
