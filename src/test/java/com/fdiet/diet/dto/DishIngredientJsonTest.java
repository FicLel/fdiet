package com.fdiet.diet.dto;

import com.fdiet.diet.helpers.MealTextParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editor sends ingredients back without the fields that are only filled on
 * the way out. A body that leaves them out has to read, or every publish fails.
 */
class DishIngredientJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MealTextParser parser = new MealTextParser();

    @Test
    void readsAnIngredientThatLeavesTheOutgoingFieldsOut() {
        DishIngredient ingredient = mapper.readValue(
                "{\"name\":\"lentejas\",\"quantity\":60,\"unit\":\"g\",\"bedcaFoodId\":1065}",
                DishIngredient.class);

        assertThat(ingredient.stateMismatch()).isFalse();
        assertThat(ingredient.bedcaFoodId()).isEqualTo(1065L);
    }

    @Test
    void writesTheUnitInBothNumbersWithItsSizeAgreeing() {
        JsonNode egg = mapper.valueToTree(only("Huevo, entero, crudo (2 unidades medianas)"));

        assertThat(egg.get("unit").asString()).isEqualTo("unidades");
        assertThat(egg.get("size").asString()).isEqualTo("MEDIUM");
        assertThat(egg.get("unitWording").get("singular").asString()).isEqualTo("unidad mediana");
        assertThat(egg.get("unitWording").get("plural").asString()).isEqualTo("unidades medianas");
        assertThat(egg.get("unitWording").get("sizeInName").asBoolean()).isFalse();
    }

    @Test
    void leavesTheSizeOutOfTheUnitWhenTheNameAlreadySaysIt() {
        JsonNode kiwi = mapper.valueToTree(only("1 kiwi mediano"));

        assertThat(kiwi.get("name").asString()).isEqualTo("kiwi mediano");
        assertThat(kiwi.get("unitWording").get("singular").asString()).isEqualTo("unidad");
        assertThat(kiwi.get("unitWording").get("plural").asString()).isEqualTo("unidades");
        assertThat(kiwi.get("unitWording").get("sizeInName").asBoolean()).isTrue();
    }

    @Test
    void readsABodyThatSendsTheWordingBackAndIgnoresIt() {
        DishIngredient ingredient = mapper.readValue(
                "{\"name\":\"Huevo\",\"quantity\":2,\"unit\":\"unidades\",\"size\":\"MEDIUM\","
                        + "\"unitWording\":{\"singular\":\"x\",\"plural\":\"y\",\"sizeInName\":true}}",
                DishIngredient.class);

        assertThat(ingredient.unitWording().plural()).isEqualTo("unidades medianas");
    }

    private DishIngredient only(String text) {
        return parser.parse(text, "Plato").ingredients().get(0);
    }
}
