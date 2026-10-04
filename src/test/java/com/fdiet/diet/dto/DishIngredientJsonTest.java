package com.fdiet.diet.dto;

import com.fdiet.diet.helpers.MealTextParser;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void readsAnIngredientThatLeavesTheOutgoingFieldsOut() {
        DishIngredient ingredient = mapper.readValue(
                "{\"name\":\"lentejas\",\"quantity\":60,\"unit\":\"g\",\"compositionFoodId\":1065}",
                DishIngredient.class);

        assertThat(ingredient.stateMismatch()).isFalse();
        assertThat(ingredient.compositionFoodId()).isEqualTo(1065L);
        assertThat(validator.validate(ingredient)).isEmpty();
    }

    /** FD-033: a BEDCA id is read only to be refused — never silently dropped, never written out. */
    @Test
    void refusesABedcaFoodIdAndNeverWritesOne() {
        DishIngredient ingredient = mapper.readValue(
                "{\"name\":\"lentejas\",\"quantity\":60,\"unit\":\"g\",\"bedcaFoodId\":1065}",
                DishIngredient.class);

        assertThat(validator.validate(ingredient))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("bedcaFoodId");
        assertThat(mapper.valueToTree(ingredient).has("bedcaFoodId")).isFalse();
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

    /** FD-054: a measure sent without the flag counts as picked, so a caller from before loses nothing. */
    @Test
    void readsAMeasureSentWithoutTheFlagAsPicked() {
        DishIngredient ingredient = mapper.readValue(
                "{\"name\":\"huevo\",\"quantity\":2,\"unit\":\"unidades\",\"foodMeasureId\":40}",
                DishIngredient.class);

        assertThat(ingredient.measurePicked()).isTrue();
        assertThat(ingredient.pickedMeasureId()).isEqualTo(40L);
    }

    /** FD-054: the rule's measure sent back is no pick, and a flag beside no measure picks nothing. */
    @Test
    void readsTheRulesMeasureAsNoPick() {
        DishIngredient rules = mapper.readValue("{\"name\":\"huevo\",\"quantity\":2,\"unit\":\"unidades\","
                + "\"foodMeasureId\":40,\"measurePicked\":false}", DishIngredient.class);
        DishIngredient none = mapper.readValue("{\"name\":\"huevo\",\"quantity\":2,\"unit\":\"unidades\","
                + "\"measurePicked\":true}", DishIngredient.class);

        assertThat(rules.pickedMeasureId()).isNull();
        assertThat(none.measurePicked()).isFalse();
        assertThat(mapper.valueToTree(rules).get("measurePicked").asBoolean()).isFalse();
    }


    private DishIngredient only(String text) {
        return parser.parse(text, "Plato").ingredients().get(0);
    }
}
