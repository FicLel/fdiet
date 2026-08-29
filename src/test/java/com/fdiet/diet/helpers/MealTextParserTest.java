package com.fdiet.diet.helpers;

import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Every sentence here is taken verbatim from example-ui.xlsx. */
class MealTextParserTest {

    private final MealTextParser parser = new MealTextParser();

    @Test
    void readsTheDishNameAndItsIngredients() {
        Dish dish = parser.parse(
                "Ensalada: lechuga (80 gr) + tomate (100 gr) + pepino (80 gr) + 1 cdta AOVE",
                "Primer plato");

        assertThat(dish.name()).isEqualTo("Ensalada");
        assertThat(dish.ingredients()).extracting(DishIngredient::name)
                .containsExactly("lechuga", "tomate", "pepino", "1 cdta AOVE");
        assertThat(dish.ingredients().get(0).quantity()).isEqualByComparingTo("80");
        assertThat(dish.ingredients().get(0).unit()).isEqualTo("gr");
    }

    @Test
    void fallsBackToOneUnitWhenNoQuantityIsGiven() {
        Dish dish = parser.parse("Ensalada: lechuga (80 gr) + 1 cdta AOVE", "Primer plato");

        DishIngredient oil = dish.ingredients().get(1);
        assertThat(oil.name()).isEqualTo("1 cdta AOVE");
        assertThat(oil.quantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(oil.unit()).isEqualTo("unidad");
    }

    @Test
    void usesTheRowLabelWhenTheTextNamesNoDish() {
        Dish dish = parser.parse(
                "1 vaso de leche semidesnatada (250 mL) + plátano pequeño (120 gr)", "Desayuno");

        assertThat(dish.name()).isEqualTo("Desayuno");
        assertThat(dish.ingredients()).extracting(DishIngredient::name)
                .containsExactly("1 vaso de leche semidesnatada", "plátano pequeño");
        assertThat(dish.ingredients().get(0).unit()).isEqualTo("mL");
        assertThat(dish.ingredients().get(0).quantity()).isEqualByComparingTo("250");
    }

    @Test
    void takesTheLastQuantityInTheBrackets() {
        Dish dish = parser.parse("2 tostadas integrales (60 gr) con aguacate (1/2 unidad, 80 gr) "
                + "+ café con leche semidesnatada (150 mL) sin azúcar", "Desayuno");

        DishIngredient toast = dish.ingredients().get(0);
        assertThat(toast.quantity()).isEqualByComparingTo("60");
        assertThat(toast.unit()).isEqualTo("gr");

        DishIngredient coffee = dish.ingredients().get(1);
        assertThat(coffee.name()).isEqualTo("café con leche semidesnatada sin azúcar");
        assertThat(coffee.quantity()).isEqualByComparingTo("150");
        assertThat(coffee.unit()).isEqualTo("mL");
    }

    @Test
    void readsAFractionAsTheQuantityWhenItIsAllThereIs() {
        Dish dish = parser.parse("aguacate (1/2 unidad)", "Desayuno");

        assertThat(dish.ingredients()).singleElement()
                .satisfies(ingredient -> {
                    assertThat(ingredient.name()).isEqualTo("aguacate");
                    assertThat(ingredient.quantity()).isEqualByComparingTo("0.50");
                    assertThat(ingredient.unit()).isEqualTo("unidad");
                });
    }

    @Test
    void doesNotSplitInsideBrackets() {
        Dish dish = parser.parse(
                "Frutos secos variados (20 g: nueces + almendras) + 1 plátano maduro (150 g)",
                "Merienda");

        assertThat(dish.ingredients()).extracting(DishIngredient::name)
                .containsExactly("Frutos secos variados", "1 plátano maduro");
    }

    @Test
    void keepsTheFirstFoodWhenTheColonComesAfterASeparator() {
        // A colon this late names a garnish, not the dish: reading it as the
        // dish name would drop the tuna the meal is built on.
        Dish dish = parser.parse("Atún al natural o a la plancha (160 gr) + judías verdes al vapor: "
                + "judías verdes (200 gr) + sal + limón", "Segundo plato");

        assertThat(dish.name()).isEqualTo("Segundo plato");
        assertThat(dish.ingredients()).extracting(DishIngredient::name)
                .containsExactly("Atún al natural o a la plancha", "judías verdes", "sal", "limón");
        assertThat(dish.ingredients().get(0).quantity()).isEqualByComparingTo("160");
    }

    @Test
    void keepsACellThatCarriesNoQuantityAtAll() {
        Dish dish = parser.parse("1 kiwi", "Postre");

        assertThat(dish.name()).isEqualTo("Postre");
        assertThat(dish.ingredients()).singleElement()
                .extracting(DishIngredient::name).isEqualTo("1 kiwi");
    }

    @Test
    void everyIngredientStartsUnresolved() {
        Dish dish = parser.parse("Ensalada: lechuga (80 gr)", "Primer plato");

        assertThat(dish.ingredients()).allSatisfy(ingredient -> {
            assertThat(ingredient.foodItemId()).isNull();
            assertThat(ingredient.resolved()).isFalse();
            assertThat(ingredient.id()).isNull();
        });
    }

    @Test
    void returnsNothingForAnEmptyCell() {
        assertThat(parser.parse("   ", "Postre")).isNull();
        assertThat(parser.parse(null, "Postre")).isNull();
    }
}
