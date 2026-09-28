package com.fdiet.diet.mapper;

import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedIngredient;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.dto.YieldFactorDto;
import com.fdiet.diet.service.IDietNutritionService;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The written cell survives the crossing in both directions.
 *
 * <p>It has to: {@code PUT /api/diets/{id}} replaces a whole week, so an editor
 * changing one cell sends the other sixty-nine back. If the sentence did not
 * come out of the database it could only be rebuilt from the parts, and a
 * rebuilt sentence is not the one that was written.
 */
class DietMapperTest {

    private static final String CELL =
            "Tostada de pan integral (60 gr) con tomate rallado (80 gr)";

    private final IDietNutritionService nutrition = mock(IDietNutritionService.class);
    private final IReferenceService reference = mock(IReferenceService.class);
    private final DietMapper mapper = new DietMapper(nutrition, reference);

    @Test
    void carriesTheWrittenCellIntoTheEntityAndBackOut() {
        PlannedDish entity = mapper.toEntity(new Dish("Tostada", CELL, List.of()));
        assertThat(entity.getRawText()).isEqualTo(CELL);

        assertThat(mapper.toDto(entity).rawText()).isEqualTo(CELL);
    }

    /** A dish nobody typed has no written form, and none is invented for it. */
    @Test
    void leavesTheCellNullWhenNothingWroteOne() {
        DishIngredient lettuce = new DishIngredient("lechuga", java.math.BigDecimal.TEN, "gr");
        PlannedDish entity = mapper.toEntity(new Dish("Ensalada", List.of(lettuce)));

        assertThat(entity.getRawText()).isNull();
        assertThat(mapper.toDto(entity).rawText()).isNull();
    }

    /** 150 g of raw breast priced against grilled breast: 108 g cooked, by USDA's 72 %, offered. */
    @Test
    void offersAYieldWhenTheTextAndTheFoodDisagreeAboutCooking() {
        BedcaFood grilled = new BedcaFood();
        grilled.setId(2297L);
        grilled.setName("Pollo, pechuga, plancha");
        PlannedIngredient raw = new PlannedIngredient("pechuga de pollo", null, grilled,
                new BigDecimal("150"), "g");
        raw.setState(FoodState.RAW);
        when(nutrition.edibleGrams(raw)).thenReturn(new BigDecimal("150"));
        when(reference.yieldFactors(eq("Pollo, pechuga, plancha"), eq("Pollo, pechuga, plancha")))
                .thenReturn(List.of(new YieldFactorDto(1L, "USDA-Y:POLLO-PECHUGA-ASADA",
                        "USDA-YIELDS-2014", "USDA 2014", null, "pollo pechuga",
                        "Chicken, broiler-fryer, breast, meat and skin",
                        "Baked or Roasted, unspecified", "asado;asada", new BigDecimal("72"), null,
                        "Tabla 1: NDB 5060", null)));

        DishIngredient read = mapper.toDto(raw);

        assertThat(read.stateMismatch()).isTrue();
        assertThat(read.yieldHint()).satisfies(hint -> {
            assertThat(hint.writtenState()).isEqualTo(FoodState.RAW);
            assertThat(hint.foodState()).isEqualTo(FoodState.COOKED);
            assertThat(hint.equivalentGrams()).isEqualByComparingTo("108");
            assertThat(hint.methodNamed()).isFalse();
        });
        // Offered, never applied: the quantity stays what was written.
        assertThat(read.quantity()).isEqualByComparingTo("150");
    }

    @Test
    void asksForNoYieldWhenNothingDisagrees() {
        BedcaFood grilled = new BedcaFood();
        grilled.setId(2297L);
        grilled.setName("Pollo, pechuga, plancha");
        PlannedIngredient plain = new PlannedIngredient("pechuga de pollo", null, grilled,
                new BigDecimal("120"), "g");

        assertThat(mapper.toDto(plain).yieldHint()).isNull();
        verify(reference, never()).yieldFactors(any(), any());
    }
}
