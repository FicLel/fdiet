package com.fdiet.food.repository;

import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.dto.NutrientDto;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.model.Nutrient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The upsert's SQL and the binder that fills it are built separately; a column
 * added to one and not the other would shift every value after it into the
 * wrong column without any error. These pin them together.
 */
class CompositionFoodUpsertRepositoryImplTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CompositionFoodUpsertRepositoryImpl repository =
            new CompositionFoodUpsertRepositoryImpl(jdbcTemplate);

    @Test
    void bindsOneValueForEveryPlaceholderInColumnOrder() throws Exception {
        CompositionLinkDto link = new CompositionLinkDto(
                new CompositionKey(CompositionSource.CIQUAL, "20031"), "Lechuga, cruda",
                List.of("lechuga", "lechugas"), true, new BigDecimal("0.64"), 169247, false);
        CompositionFoodRowDto row = new CompositionFoodRowDto(
                new CompositionKey(CompositionSource.CIQUAL, "20031"), "Laitue, crue", "Lettuce, raw",
                "0201", Map.of(Nutrient.ENERGY, new NutrientDto(new BigDecimal("14.7"), "kcal"),
                Nutrient.VITAMIN_C, new NutrientDto(new BigDecimal("3.7"), "mg")), link);

        repository.upsertAll(List.of(row), 500);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<CompositionFoodRowDto>> setter =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sql.capture(), anyList(), eq(500), setter.capture());

        int placeholders = (int) sql.getValue().chars().filter(c -> c == '?').count();
        // source, source_code, 9 names / crosswalk columns, and a value + unit per component.
        assertThat(placeholders).isEqualTo(2 + 9 + 2 * Nutrient.values().length);

        PreparedStatement statement = mock(PreparedStatement.class);
        setter.getValue().setValues(statement, row);

        verify(statement).setString(1, "CIQUAL");
        verify(statement).setString(2, "20031");
        verify(statement).setString(3, "Laitue, crue");
        verify(statement).setString(7, "lechuga;lechugas");
        verify(statement).setBoolean(8, true);
        verify(statement).setBigDecimal(10, new BigDecimal("0.64"));
        verify(statement).setObject(11, 169247);
        // The first and last component land in the last placeholders, value then unit.
        verify(statement).setBigDecimal(12, new BigDecimal("14.7"));
        verify(statement).setString(13, "kcal");
        verify(statement).setBigDecimal(placeholders - 1, new BigDecimal("3.7"));
        verify(statement).setString(placeholders, "mg");
    }

    /** A re-sync writes over everything but the key, and never the id. */
    @Test
    void updatesEveryColumnButTheKeyAndTheId() {
        repository.upsertAll(List.of(), 500);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).batchUpdate(sql.capture(), anyList(), anyInt(),
                org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<CompositionFoodRowDto>>any());
        String update = sql.getValue().substring(sql.getValue().indexOf("ON DUPLICATE KEY UPDATE"));

        assertThat(update).doesNotContain("source =", "source_code =", " id =")
                .contains("name_es = incoming.name_es", "energy_unit = incoming.energy_unit",
                        "vitamin_c = incoming.vitamin_c");
    }
}
