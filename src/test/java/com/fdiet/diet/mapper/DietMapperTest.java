package com.fdiet.diet.mapper;

import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.service.IDietNutritionService;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

    private final DietMapper mapper = new DietMapper(mock(IDietNutritionService.class), mock(IReferenceService.class));

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
}
