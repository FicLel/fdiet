package com.fdiet.diet.helpers;

import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.PortionSize;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Every sentence here is taken verbatim from example-ui.xlsx, or written the way the composer writes one. */
class MealTextParserTest {

    private final MealTextParser parser = new MealTextParser();

    @Test
    void readsTheDishNameAndItsIngredients() {
        RecipeDto dish = parser.parse(
                "Ensalada: lechuga (80 gr) + tomate (100 gr) + pepino (80 gr) + 1 cdta AOVE",
                "Primer plato");

        assertThat(dish.name()).isEqualTo("Ensalada");
        assertThat(dish.ingredients()).extracting(DishIngredient::name)
                .containsExactly("lechuga", "tomate", "pepino", "AOVE");
        assertThat(dish.ingredients().get(0).quantity()).isEqualByComparingTo("80");
        assertThat(dish.ingredients().get(0).unit()).isEqualTo("gr");
    }

    @Test
    void readsACountAndAHouseholdMeasureWrittenInFrontOfTheFood() {
        RecipeDto dish = parser.parse("Ensalada: lechuga (80 gr) + 1 cdta AOVE", "Primer plato");

        DishIngredient oil = dish.ingredients().get(1);
        assertThat(oil.name()).isEqualTo("AOVE");
        assertThat(oil.quantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(oil.unit()).isEqualTo("cdta");
    }

    /** What the composer writes for a food added in units, weighed by the nutritionist's criterion. */
    @Test
    void readsBackAFoodTheComposerWroteInUnits() {
        RecipeDto dish = parser.parse("Huevo, entero, crudo (2 unidades medianas)", "Huevo");

        DishIngredient eggs = dish.ingredients().get(0);
        assertThat(eggs.name()).isEqualTo("Huevo, entero, crudo");
        assertThat(eggs.quantity()).isEqualByComparingTo("2");
        assertThat(eggs.unit()).isEqualTo("unidades");
        assertThat(eggs.size()).isEqualTo(PortionSize.MEDIUM);
    }

    @Test
    void fallsBackToOneUnitWhenNoQuantityIsGiven() {
        RecipeDto dish = parser.parse("Pescado (120 gr) + sal + limón", "Segundo plato");

        DishIngredient salt = dish.ingredients().get(1);
        assertThat(salt.name()).isEqualTo("sal");
        assertThat(salt.quantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(salt.unit()).isEqualTo("unidad");
    }

    @Test
    void usesTheRowLabelWhenTheTextNamesNoDish() {
        RecipeDto dish = parser.parse(
                "1 vaso de leche semidesnatada (250 mL) + plátano pequeño (120 gr)", "Desayuno");

        assertThat(dish.name()).isEqualTo("Desayuno");
        assertThat(dish.ingredients()).extracting(DishIngredient::name)
                .containsExactly("1 vaso de leche semidesnatada", "plátano pequeño");
        assertThat(dish.ingredients().get(0).unit()).isEqualTo("mL");
        assertThat(dish.ingredients().get(0).quantity()).isEqualByComparingTo("250");
    }

    @Test
    void takesTheLastQuantityInTheBrackets() {
        RecipeDto dish = parser.parse("2 tostadas integrales (60 gr) con aguacate (1/2 unidad, 80 gr) "
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
        RecipeDto dish = parser.parse("aguacate (1/2 unidad)", "Desayuno");

        assertThat(dish.ingredients()).singleElement()
                .satisfies(ingredient -> {
                    assertThat(ingredient.name()).isEqualTo("aguacate");
                    assertThat(ingredient.quantity()).isEqualByComparingTo("0.50");
                    assertThat(ingredient.unit()).isEqualTo("unidad");
                });
    }

    @Test
    void doesNotSplitInsideBrackets() {
        RecipeDto dish = parser.parse(
                "Frutos secos variados (20 g: nueces + almendras) + 1 plátano maduro (150 g)",
                "Merienda");

        assertThat(dish.ingredients()).extracting(DishIngredient::name)
                .containsExactly("Frutos secos variados", "1 plátano maduro");
    }

    @Test
    void keepsTheFirstFoodWhenTheColonComesAfterASeparator() {
        // A colon this late names a garnish, not the dish: reading it as the
        // dish name would drop the tuna the meal is built on.
        RecipeDto dish = parser.parse("Atún al natural o a la plancha (160 gr) + judías verdes al vapor: "
                + "judías verdes (200 gr) + sal + limón", "Segundo plato");

        assertThat(dish.name()).isEqualTo("Segundo plato");
        assertThat(dish.ingredients()).extracting(DishIngredient::name)
                .containsExactly("Atún al natural o a la plancha", "judías verdes", "sal", "limón");
        assertThat(dish.ingredients().get(0).quantity()).isEqualByComparingTo("160");
    }

    @Test
    void readsALeadingCountAsThatManyPieces() {
        RecipeDto dish = parser.parse("1 kiwi", "Postre");

        assertThat(dish.name()).isEqualTo("Postre");
        assertThat(dish.ingredients()).singleElement().satisfies(kiwi -> {
            assertThat(kiwi.name()).isEqualTo("kiwi");
            assertThat(kiwi.quantity()).isEqualByComparingTo("1");
            assertThat(kiwi.unit()).isEqualTo("unidad");
        });
    }

    @Test
    void readsTheMeasureAndDropsTheLinkingDe() {
        DishIngredient turkey = parser.parse("2 lonchas de pavo", "Merienda").ingredients().get(0);

        assertThat(turkey.name()).isEqualTo("pavo");
        assertThat(turkey.quantity()).isEqualByComparingTo("2");
        assertThat(turkey.unit()).isEqualTo("lonchas");
    }

    @Test
    void readsAWeightWrittenInFrontOfTheFood() {
        DishIngredient rice = parser.parse("80 g de arroz", "Comida").ingredients().get(0);

        assertThat(rice.name()).isEqualTo("arroz");
        assertThat(rice.quantity()).isEqualByComparingTo("80");
        assertThat(rice.unit()).isEqualTo("g");
    }

    @Test
    void keepsTheSizeOfAPieceBesideItsName() {
        DishIngredient pear = parser.parse("1 pera pequeña", "Postre").ingredients().get(0);

        assertThat(pear.name()).isEqualTo("pera pequeña");
        assertThat(pear.size()).isEqualTo(PortionSize.SMALL);
    }

    @Test
    void keepsTheRawOrCookedWordInsteadOfDroppingIt() {
        RecipeDto dish = parser.parse("arroz blanco (70 g crudo) + lentejas cocidas (180 gr) "
                + "+ garbanzos (100 g cocidos sin piel) + 1 huevo cocido", "Comida");

        assertThat(dish.ingredients()).extracting(DishIngredient::state).containsExactly(
                FoodState.RAW, FoodState.COOKED, FoodState.COOKED, FoodState.COOKED);
        // The bracketed fragments store exactly what they stored before.
        assertThat(dish.ingredients().get(0).name()).isEqualTo("arroz blanco");
        assertThat(dish.ingredients().get(0).quantity()).isEqualByComparingTo("70");
        assertThat(dish.ingredients().get(1).name()).isEqualTo("lentejas cocidas");
    }

    @Test
    void readsDryWeightOnlyWhereSecoCannotBeTheFood() {
        RecipeDto dish = parser.parse("pasta (70 g en seco) + frutos secos (20 g)", "Comida");

        assertThat(dish.ingredients().get(0).state()).isEqualTo(FoodState.DRY);
        assertThat(dish.ingredients().get(1).state()).isNull();
    }

    @Test
    void leavesTheStateUnknownWhenTheTextSaysBoth() {
        DishIngredient lentils = parser.parse("lentejas (60 g en crudo, 180 g cocidas)", "Comida")
                .ingredients().get(0);

        assertThat(lentils.state()).isNull();
        assertThat(lentils.quantity()).isEqualByComparingTo("180");
    }

    @Test
    void readsAHouseholdMeasureOfSeveralWordsInsideBrackets() {
        DishIngredient oil = parser.parse("aceite de oliva virgen extra (1 cucharada sopera)", "Comida")
                .ingredients().get(0);

        assertThat(oil.unit()).isEqualTo("cucharada sopera");
        assertThat(oil.quantity()).isEqualByComparingTo("1");
    }

    @Test
    void readsTheComposedFragmentsBack() {
        DishIngredient avocado = parser.parse("Aguacate (1/2 unidad mediana)", "Desayuno")
                .ingredients().get(0);

        assertThat(avocado.name()).isEqualTo("Aguacate");
        assertThat(avocado.quantity()).isEqualByComparingTo("0.50");
        assertThat(avocado.unit()).isEqualTo("unidad");
        assertThat(avocado.size()).isEqualTo(PortionSize.MEDIUM);
    }

    @Test
    void everyIngredientStartsUnresolved() {
        RecipeDto dish = parser.parse("Ensalada: lechuga (80 gr)", "Primer plato");

        assertThat(dish.ingredients()).allSatisfy(ingredient -> {
            assertThat(ingredient.foodItemId()).isNull();
            assertThat(ingredient.resolved()).isFalse();
            assertThat(ingredient.id()).isNull();
        });
    }

    /**
     * The parts do not add back up to the sentence — this is the cell that
     * showed it, and why {@code raw_text} exists.
     */
    @Test
    void keepsTheCellAsItWasWritten() {
        String cell = "Tostada de pan integral (60 gr) con tomate rallado (80 gr) "
                + "y 3 lonchas de pavo (60 gr)";

        RecipeDto dish = parser.parse("  " + cell + "  ", "Desayuno");

        assertThat(dish.rawText()).isEqualTo(cell);
        assertThat(dish.ingredients()).extracting(DishIngredient::name)
                .doesNotContain(cell);
    }

    @Test
    void readsARangeInBracketsAsBothEndsInsteadOfTheLastNumber() {
        DishIngredient turkey = parser.parse("2-3 lonchas de pavo (40-60 gr)", "Merienda")
                .ingredients().get(0);

        assertThat(turkey.name()).isEqualTo("2-3 lonchas de pavo");
        assertThat(turkey.quantity()).isEqualByComparingTo("40");
        assertThat(turkey.quantityMax()).isEqualByComparingTo("60");
        assertThat(turkey.unit()).isEqualTo("gr");
        assertThat(turkey.range()).isTrue();
    }

    @Test
    void readsARangeOfPiecesWrittenInFrontOfTheFood() {
        DishIngredient nuts = parser.parse("2-3 nueces", "Media mañana").ingredients().get(0);
        DishIngredient spoons = parser.parse("1 a 2 cdta AOVE", "Comida").ingredients().get(0);

        assertThat(nuts.name()).isEqualTo("nueces");
        assertThat(nuts.quantity()).isEqualByComparingTo("2");
        assertThat(nuts.quantityMax()).isEqualByComparingTo("3");
        assertThat(nuts.unit()).isEqualTo("unidad");
        assertThat(spoons.name()).isEqualTo("AOVE");
        assertThat(spoons.quantityMax()).isEqualByComparingTo("2");
        assertThat(spoons.unit()).isEqualTo("cdta");
    }

    @Test
    void aSingleValueAndABackwardsRangeAreNoRange() {
        assertThat(parser.parse("lechuga (80 gr)", "Comida").ingredients().get(0).quantityMax())
                .isNull();
        assertThat(parser.parse("1 kiwi", "Postre").ingredients().get(0).quantityMax()).isNull();
        DishIngredient backwards = parser.parse("arroz (60-40 gr)", "Comida").ingredients().get(0);
        assertThat(backwards.quantity()).isEqualByComparingTo("60");
        assertThat(backwards.quantityMax()).isNull();
    }

    @Test
    void anAWordThatIsNotARangeIsLeftToTheFood() {
        DishIngredient fish = parser.parse("1 a la plancha", "Cena").ingredients().get(0);

        assertThat(fish.quantity()).isEqualByComparingTo("1");
        assertThat(fish.quantityMax()).isNull();
        assertThat(fish.name()).isEqualTo("a la plancha");
    }

    @Test
    void returnsNothingForAnEmptyCell() {
        assertThat(parser.parse("   ", "Postre")).isNull();
        assertThat(parser.parse(null, "Postre")).isNull();
    }
}
