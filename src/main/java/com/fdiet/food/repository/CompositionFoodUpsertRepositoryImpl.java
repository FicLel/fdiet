package com.fdiet.food.repository;

import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.dto.NutrientDto;
import com.fdiet.food.model.Nutrient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plain JDBC, because the table's id is {@code AUTO_INCREMENT}: Hibernate does
 * not batch inserts of an identity-generated entity, and an upsert keyed on the
 * unique {@code (source, source_code)} is one statement for both a first sync
 * and a re-sync. It runs inside the caller's transaction — the
 * {@link JdbcTemplate} and the JPA transaction share the data source.
 */
class CompositionFoodUpsertRepositoryImpl implements CompositionFoodUpsertRepository {

    /** Written on insert only: the key a re-sync finds the row by. */
    private static final List<String> KEY_COLUMNS = List.of("source", "source_code");

    /** Written on insert and on every re-sync, in the order {@link #bind} sets them. */
    private static final List<String> VALUE_COLUMNS = valueColumns();

    private static final String UPSERT = upsertSql();

    private final JdbcTemplate jdbcTemplate;

    CompositionFoodUpsertRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsertAll(List<CompositionFoodRowDto> rows, int batchSize) {
        jdbcTemplate.batchUpdate(UPSERT, rows, batchSize, CompositionFoodUpsertRepositoryImpl::bind);
    }

    private static void bind(PreparedStatement statement, CompositionFoodRowDto row) throws SQLException {
        CompositionLinkDto link = row.link();
        int at = 0;
        statement.setString(++at, row.key().source().name());
        statement.setString(++at, row.key().sourceCode());
        statement.setString(++at, row.nameOriginal());
        statement.setString(++at, row.nameEn());
        statement.setString(++at, row.foodGroupCode());
        statement.setString(++at, link == null ? null : link.nameEs());
        statement.setString(++at, link == null ? null : link.aliasesJoined());
        statement.setBoolean(++at, link != null && link.preferred());
        statement.setBoolean(++at, link != null && link.reviewed());
        statement.setBigDecimal(++at, link == null ? null : link.ediblePortion());
        statement.setObject(++at, link == null ? null : link.ediblePortionFdcId());
        for (Nutrient nutrient : Nutrient.values()) {
            NutrientDto published = row.nutrients().get(nutrient);
            statement.setBigDecimal(++at, published == null ? null : published.value());
            statement.setString(++at, published == null ? null : published.unit());
        }
    }

    private static List<String> valueColumns() {
        List<String> columns = new ArrayList<>(List.of(
                "name_original", "name_en", "food_group_code",
                "name_es", "name_aliases", "name_preferred", "name_reviewed",
                "edible_portion", "edible_portion_fdc_id"));
        for (Nutrient nutrient : Nutrient.values()) {
            columns.add(nutrient.column());
            columns.add(nutrient.column() + Nutrient.UNIT_COLUMN_SUFFIX);
        }
        return List.copyOf(columns);
    }

    /** MySQL 8.0.19+ row alias: {@code … AS incoming ON DUPLICATE KEY UPDATE c = incoming.c}. */
    private static String upsertSql() {
        List<String> all = new ArrayList<>(KEY_COLUMNS);
        all.addAll(VALUE_COLUMNS);
        return "INSERT INTO composition_foods (" + String.join(", ", all) + ") VALUES ("
                + all.stream().map(column -> "?").collect(Collectors.joining(", "))
                + ") AS incoming ON DUPLICATE KEY UPDATE "
                + VALUE_COLUMNS.stream().map(column -> column + " = incoming." + column)
                .collect(Collectors.joining(", "));
    }
}
