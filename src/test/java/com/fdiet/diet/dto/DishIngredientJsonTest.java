package com.fdiet.diet.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editor sends ingredients back without the fields that are only filled on
 * the way out. A body that leaves them out has to read, or every publish fails.
 */
class DishIngredientJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsAnIngredientThatLeavesTheOutgoingFieldsOut() {
        DishIngredient ingredient = mapper.readValue(
                "{\"name\":\"lentejas\",\"quantity\":60,\"unit\":\"g\",\"bedcaFoodId\":1065}",
                DishIngredient.class);

        assertThat(ingredient.stateMismatch()).isFalse();
        assertThat(ingredient.bedcaFoodId()).isEqualTo(1065L);
    }
}
