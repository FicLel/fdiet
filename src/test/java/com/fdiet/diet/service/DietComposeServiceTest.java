package com.fdiet.diet.service;

import com.fdiet.diet.dto.ComposeRequestDto;
import com.fdiet.diet.dto.ComposedFragmentDto;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.mapper.ReferenceMapper;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Añadir por raciones" with the nutritionist's global criterion: the fragment
 * is written in units the patient can follow, and it works before the diet
 * exists — no diet id, so no diet's criteria and no profile.
 */
class DietComposeServiceTest {

    private static final long EGG = 2127L;
    private static final long CRITERION_ID = 40L;

    private final IRecipeService recipeService = mock(IRecipeService.class);
    private final IBedcaFoodService bedcaFoodService = mock(IBedcaFoodService.class);
    private final IReferenceService referenceService = mock(IReferenceService.class);
    private final IMeasureResolverService measureResolver = mock(IMeasureResolverService.class);

    private final DietComposeService composeService = new DietComposeService(
            mock(IDietService.class),
            recipeService,
            bedcaFoodService,
            referenceService,
            measureResolver);

    private final ReferenceFoodMeasure criterion = globalEgg();
    private final FoodMeasureDto described = new ReferenceMapper().toDto(criterion);

    @BeforeEach
    void stubs() {
        BedcaFood egg = new BedcaFood();
        egg.setId(EGG);
        egg.setName("Huevo, entero, crudo");
        when(bedcaFoodService.entityById(EGG)).thenReturn(egg);
        when(referenceService.measureEntities(List.of(CRITERION_ID))).thenReturn(Map.of(CRITERION_ID, criterion));
        when(referenceService.describe(criterion)).thenReturn(described);
        when(recipeService.read(anyString(), anyString(), isNull(), isNull(), eq(CRITERION_ID)))
                .thenReturn(new RecipeDto(null, "Huevo", false, null, null,
                        List.of(mock(DishIngredient.class)), null));
    }

    @Test
    void writesTheFoodInUnitsWithNoDietYet() {
        when(measureResolver.choose(any(), eq("unidad"), eq(PortionSize.MEDIUM), eq(CRITERION_ID), isNull(),
                isNull())).thenReturn(new MeasureChoiceDto(described, List.of(described)));

        ComposedFragmentDto composed = composeService.compose(
                new ComposeRequestDto(EGG, null, CRITERION_ID, new BigDecimal("2"), null, null));

        assertThat(composed.fragment()).isEqualTo("Huevo, entero, crudo (2 unidades medianas)");
        verify(recipeService).read("Huevo, entero, crudo (2 unidades medianas)", "Huevo, entero, crudo",
                null, null, CRITERION_ID);
    }

    @Test
    void refusesAMeasureThatDoesNotWeighTheFood() {
        when(measureResolver.choose(any(), any(), any(), any(), any(), any()))
                .thenReturn(MeasureChoiceDto.NONE);

        assertThatThrownBy(() -> composeService.compose(
                new ComposeRequestDto(EGG, null, CRITERION_ID, BigDecimal.ONE, null, null)))
                .isInstanceOf(InvalidDietException.class);
    }

    private static ReferenceFoodMeasure globalEgg() {
        ReferenceFoodMeasure row = new ReferenceFoodMeasure();
        row.setId(CRITERION_ID);
        row.setGlobalCriterion(true);
        row.setMeasure(HouseholdMeasure.UNIDAD);
        row.setSize(PortionSize.MEDIUM);
        row.setCount(BigDecimal.ONE);
        row.setBedcaFoodId(EGG);
        row.setFoodLabel("Huevo, entero, crudo");
        row.setGramsMin(new BigDecimal("58"));
        row.setGramsMax(new BigDecimal("58"));
        row.setState(FoodState.UNSPECIFIED);
        row.setWeightBasis(WeightBasis.NET_EDIBLE);
        return row;
    }
}
