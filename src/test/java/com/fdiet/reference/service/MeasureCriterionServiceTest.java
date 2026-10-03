package com.fdiet.reference.service;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.MeasureUser;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureCriterionRequestDto;
import com.fdiet.reference.dto.MeasureUsageDto;
import com.fdiet.reference.exception.InvalidReferenceException;
import com.fdiet.reference.exception.ReferenceNotFoundException;
import com.fdiet.reference.mapper.ReferenceMapper;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.repository.ReferenceFoodMeasureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The nutritionist's global criteria: hers for every diet, one per food, measure
 * and size, never deleted from under an ingredient it weighs, and never moved to
 * another food while it weighs anything.
 */
class MeasureCriterionServiceTest {

    /** Composition food ids: CIQUAL's whole raw egg and a white sliced bread. */
    private static final long EGG = 501L;
    private static final long BREAD = 502L;
    /** A composition food the crosswalk names no Spanish name for. */
    private static final long UNNAMED = 503L;
    private static final long CRITERION_ID = 40L;

    private final ReferenceFoodMeasureRepository repository = mock(ReferenceFoodMeasureRepository.class);
    private final ICompositionFoodService compositionFoodService = mock(ICompositionFoodService.class);
    private final IFoodCategoriser categoriser = mock(IFoodCategoriser.class);
    private final IMeasureUsageCounter ingredients = counter(MeasureUser.RECIPE_INGREDIENT);
    private final IMeasureUsageCounter extras = counter(MeasureUser.EXTRA_FOOD);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<IMeasureUsageCounter> counters = mock(ObjectProvider.class);

    private final MeasureCriterionService service = new MeasureCriterionService(repository,
            new ReferenceMapper(), categoriser, compositionFoodService, counters);

    @BeforeEach
    void wireCounters() {
        when(counters.orderedStream()).thenAnswer(call -> Stream.of(ingredients, extras));
        when(compositionFoodService.entityById(EGG)).thenReturn(food(EGG, "Huevo, entero, crudo"));
        when(compositionFoodService.entityById(BREAD)).thenReturn(food(BREAD, "Pan de molde, blanco"));
        when(compositionFoodService.entityById(UNNAMED)).thenReturn(food(UNNAMED, null));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void writesAGlobalCriterionThatBelongsToNoDietAndNoSource() {
        when(repository.findByGlobalCriterionTrueAndCompositionFoodIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of());

        FoodMeasureDto saved = service.createGlobal(eggs("58"));

        assertThat(saved.globalOwn()).isTrue();
        assertThat(saved.dietOwn()).isFalse();
        assertThat(saved.dietId()).isNull();
        assertThat(saved.code()).isNull();
        assertThat(saved.sourceCode()).isNull();
        assertThat(saved.gramsPerMeasure()).isEqualByComparingTo("58");
        assertThat(saved.householdText()).isEqualTo("1 unidad mediana");
    }

    /** The criterion names the composition food by id, and reads its family off the Spanish name. */
    @Test
    void keysACriterionOnItsCompositionFood() {
        when(repository.findByGlobalCriterionTrueAndCompositionFoodIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of());
        when(categoriser.of("Huevo, entero, crudo")).thenReturn(FoodCategory.EGG);

        FoodMeasureDto saved = service.createGlobal(eggs("58"));

        assertThat(saved.compositionFoodId()).isEqualTo(EGG);
        assertThat(saved.foodLabel()).isEqualTo("Huevo, entero, crudo");
        assertThat(saved.foodCategory()).isEqualTo(FoodCategory.EGG);
    }

    /** No Spanish name yet: labelled by its English one, in no family, still weighing that food by id. */
    @Test
    void keysACriterionOnACompositionFoodWithoutASpanishName() {
        when(repository.findByGlobalCriterionTrueAndCompositionFoodIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of());

        FoodMeasureDto saved = service.createGlobal(new MeasureCriterionRequestDto(
                HouseholdMeasure.UNIDAD, null, UNNAMED, new BigDecimal("40"), null, null));

        assertThat(saved.compositionFoodId()).isEqualTo(UNNAMED);
        assertThat(saved.foodLabel()).isEqualTo("Food " + UNNAMED);
        assertThat(saved.foodCategory()).isNull();
        verify(categoriser, never()).of(any());
    }

    @Test
    void refusesASecondCriterionForTheSameFoodMeasureAndSize() {
        when(repository.findByGlobalCriterionTrueAndCompositionFoodIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(stored("58")));

        assertThatThrownBy(() -> service.createGlobal(eggs("60")))
                .isInstanceOf(InvalidReferenceException.class)
                .hasMessageContaining("id " + CRITERION_ID);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToDeleteACriterionThatStillWeighsSomething() {
        when(repository.findById(CRITERION_ID)).thenReturn(Optional.of(stored("58")));
        when(ingredients.countUsing(CRITERION_ID)).thenReturn(3L);
        when(extras.countUsing(CRITERION_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteGlobal(CRITERION_ID))
                .isInstanceOf(InvalidReferenceException.class)
                .hasMessageContaining("3 recipe ingredient(s)")
                .hasMessageContaining("1 logged extra(s)")
                .hasMessageContaining("/api/reference/criteria/" + CRITERION_ID + "/usage");
        verify(repository, never()).delete(any());
    }

    @Test
    void deletesACriterionNothingIsWeighedBy() {
        ReferenceFoodMeasure criterion = stored("58");
        when(repository.findById(CRITERION_ID)).thenReturn(Optional.of(criterion));

        service.deleteGlobal(CRITERION_ID);

        verify(repository).delete(criterion);
    }

    @Test
    void saysHowFarAChangeReaches() {
        when(repository.findById(CRITERION_ID)).thenReturn(Optional.of(stored("58")));
        when(ingredients.countUsing(CRITERION_ID)).thenReturn(5L);
        when(extras.countUsing(CRITERION_ID)).thenReturn(2L);

        assertThat(service.usage(CRITERION_ID)).isEqualTo(new MeasureUsageDto(CRITERION_ID, 5, 2));
    }

    /** A new weight is live for every ingredient it weighs; that is what the usage count warns about. */
    @Test
    void changesTheWeightOfACriterionInUse() {
        ReferenceFoodMeasure criterion = stored("58");
        when(repository.findById(CRITERION_ID)).thenReturn(Optional.of(criterion));
        when(ingredients.countUsing(CRITERION_ID)).thenReturn(3L);

        FoodMeasureDto saved = service.updateGlobal(CRITERION_ID, eggs("60"));

        assertThat(saved.gramsPerMeasure()).isEqualByComparingTo("60");
        assertThat(criterion.isGlobalCriterion()).isTrue();
    }

    /** Moving it would weigh three eggs as three slices of bread. */
    @Test
    void refusesToMoveACriterionInUseToAnotherFood() {
        when(repository.findById(CRITERION_ID)).thenReturn(Optional.of(stored("58")));
        when(ingredients.countUsing(CRITERION_ID)).thenReturn(3L);
        MeasureCriterionRequestDto bread = new MeasureCriterionRequestDto(HouseholdMeasure.REBANADA,
                null, BREAD, new BigDecimal("30"), null, null);

        assertThatThrownBy(() -> service.updateGlobal(CRITERION_ID, bread))
                .isInstanceOf(InvalidReferenceException.class)
                .hasMessageContaining("3 recipe ingredient(s)");
        verify(repository, never()).saveAndFlush(any());
    }

    /** A diet's criterion is reached through its diet, never through the global endpoints. */
    @Test
    void aDietsCriterionIsNoGlobalCriterion() {
        ReferenceFoodMeasure dietOwn = stored("58");
        dietOwn.setGlobalCriterion(false);
        dietOwn.setDietId(5L);
        when(repository.findById(CRITERION_ID)).thenReturn(Optional.of(dietOwn));

        assertThatThrownBy(() -> service.deleteGlobal(CRITERION_ID))
                .isInstanceOf(ReferenceNotFoundException.class);
    }

    @Test
    void refusesACriterionWithoutExactlyOneWeight() {
        MeasureCriterionRequestDto both = new MeasureCriterionRequestDto(HouseholdMeasure.UNIDAD,
                PortionSize.MEDIUM, EGG, new BigDecimal("58"), new BigDecimal("58"), null);

        assertThatThrownBy(() -> service.createGlobal(both)).isInstanceOf(InvalidReferenceException.class);
    }

    private static MeasureCriterionRequestDto eggs(String grams) {
        return new MeasureCriterionRequestDto(HouseholdMeasure.UNIDAD, PortionSize.MEDIUM, EGG,
                new BigDecimal(grams), null, null);
    }

    private static ReferenceFoodMeasure stored(String grams) {
        ReferenceFoodMeasure row = new ReferenceFoodMeasure();
        row.setId(CRITERION_ID);
        row.setGlobalCriterion(true);
        row.setMeasure(HouseholdMeasure.UNIDAD);
        row.setSize(PortionSize.MEDIUM);
        row.setCount(BigDecimal.ONE);
        row.setCompositionFoodId(EGG);
        row.setFoodLabel("Huevo, entero, crudo");
        row.setHouseholdText("1 unidad mediana");
        row.setGramsMin(new BigDecimal(grams));
        row.setGramsMax(new BigDecimal(grams));
        return row;
    }

    private static CompositionFood food(long id, String nameEs) {
        CompositionFood food = new CompositionFood();
        food.setId(id);
        food.setSource(CompositionSource.CIQUAL);
        food.setSourceCode(String.valueOf(id));
        food.setNameOriginal("Aliment " + id);
        food.setNameEn("Food " + id);
        food.setNameEs(nameEs);
        return food;
    }

    private static IMeasureUsageCounter counter(MeasureUser user) {
        IMeasureUsageCounter counter = mock(IMeasureUsageCounter.class);
        when(counter.user()).thenReturn(user);
        return counter;
    }
}
