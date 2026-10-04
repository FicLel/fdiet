package com.fdiet.diet.helpers;

import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.KeptMatchDto;
import com.fdiet.diet.exception.InvalidDietException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FD-048: a match survives a re-read while its ingredient keeps its name. */
class KeptMatchesTest {

    @Test
    void anUnchangedNameKeepsItsFoodAndMeasureIgnoringCaseAndAccents() {
        List<DishIngredient> applied = KeptMatches.apply(
                List.of(ingredient("plátano"), ingredient("lechuga")),
                List.of(new KeptMatchDto("Platano ", 31L, null, 40L)));

        assertThat(applied.get(0).compositionFoodId()).isEqualTo(31L);
        assertThat(applied.get(0).foodMeasureId()).isEqualTo(40L);
        // FD-054: a measure a keep entry carries is a person's pick.
        assertThat(applied.get(0).measurePicked()).isTrue();
        assertThat(applied.get(1).resolved()).isFalse();
    }

    @Test
    void anEditedNameKeepsNothing() {
        List<DishIngredient> applied = KeptMatches.apply(List.of(ingredient("lechuga romana")),
                List.of(new KeptMatchDto("lechuga", 7L, null, null)));

        assertThat(applied.get(0).resolved()).isFalse();
    }

    @Test
    void theSameNameTwiceIsPairedInOrderOfAppearance() {
        List<DishIngredient> applied = KeptMatches.apply(
                List.of(ingredient("huevo"), ingredient("huevo"), ingredient("huevo")),
                List.of(new KeptMatchDto("huevo", 1L, null, null), new KeptMatchDto("huevo", 2L, null, null)));

        assertThat(applied).extracting(DishIngredient::compositionFoodId).containsExactly(1L, 2L, null);
    }

    @Test
    void aKeptBrandedProductIsMatchedAsOne() {
        DishIngredient applied = KeptMatches.apply(List.of(ingredient("yogur")),
                List.of(new KeptMatchDto("yogur", null, 900L, null))).get(0);

        assertThat(applied.foodItemId()).isEqualTo(900L);
        assertThat(applied.compositionFoodId()).isNull();
    }

    @Test
    void refusesAKeptMatchNamingBothFoodsOrNeither() {
        List<DishIngredient> parsed = List.of(ingredient("huevo"));

        assertThatThrownBy(() -> KeptMatches.apply(parsed, List.of(new KeptMatchDto("huevo", 1L, 9L, null))))
                .isInstanceOf(InvalidDietException.class)
                .hasMessageContaining("compositionFoodId");
        assertThatThrownBy(() -> KeptMatches.apply(parsed,
                List.of(new KeptMatchDto("huevo", null, null, null))))
                .isInstanceOf(InvalidDietException.class);
    }

    private static DishIngredient ingredient(String name) {
        return new DishIngredient(name, BigDecimal.ONE, "unidad");
    }
}
