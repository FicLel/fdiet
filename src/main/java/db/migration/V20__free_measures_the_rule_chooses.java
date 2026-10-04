package db.migration;

import com.fdiet.alternative.helpers.FoodCategoriser;
import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.helpers.ReferenceMatcher;
import com.fdiet.reference.mapper.ReferenceMapper;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.model.ReferenceSource;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FD-054, the second half of V19: a stored measure the rule would choose today
 * anyway is marked as the rule's ({@code measure_picked = FALSE}), so it follows
 * the diet and global criteria written from now on. Every other stored measure
 * stays picked, because nothing recorded whether a person chose it and losing a
 * person's choice is worse than one measure not following a new criterion.
 *
 * <p>"The rule" is the one a publish applies — {@link ReferenceMatcher#chooseMeasure}
 * with no preference, over the published rows, the row's diet's own criteria (none
 * for a library recipe, which no diet's criterion weighs), the global criteria and
 * the diet's profile source — the same pieces {@code ReferenceService} hands it,
 * read here with plain JDBC because no bean exists while Flyway runs. Anything the
 * rule does not choose, or chooses differently, is left picked.
 *
 * <p>Why a migration, like V18: it has to run exactly once per database, before
 * anybody re-weighs a week, and Flyway's history is the record that it did. On a
 * fresh database it finds no rows.
 *
 * <p>O(m + n) over the measure rows and the stored rows: four selects and one
 * batched update per table.
 */
public class V20__free_measures_the_rule_chooses extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V20__free_measures_the_rule_chooses.class);

    private static final String MEASURES = "SELECT m.id, m.code, m.diet_id, m.global_criterion, "
            + "m.measure, m.size, m.measure_count, m.composition_food_id, m.food_category, "
            + "m.keywords, m.food_label, m.grams_min, m.grams_max, m.ml_min, m.ml_max, m.state, "
            + "m.weight_basis, m.gross_grams, m.household_text, m.page_ref, m.note, "
            + "s.code AS source_code, s.short_name AS source_short_name, s.tier AS source_tier "
            + "FROM ref_food_measures m LEFT JOIN ref_sources s ON s.id = m.source_id ORDER BY m.id";

    private static final String PROFILE_SOURCES = "SELECT p.code, s.code AS source_code "
            + "FROM ref_populations p JOIN ref_sources s ON s.id = p.source_id";

    /**
     * A private recipe is one plate's, so it reads its diet's criteria and profile;
     * a library recipe is weighed without any diet's, as the recipe service does.
     */
    private static final String INGREDIENTS = "SELECT i.id, i.unit, i.portion_size, "
            + "i.food_measure_id, i.composition_food_id, f.name_es, p.diet_id, p.profile "
            + "FROM recipe_ingredients i "
            + "JOIN recipes r ON r.id = i.recipe_id "
            + "JOIN composition_foods f ON f.id = i.composition_food_id "
            + "LEFT JOIN (SELECT DISTINCT d.recipe_id, m.diet_id, t.reference_profile_code AS profile "
            + "FROM diet_dishes d JOIN diet_meals m ON m.id = d.meal_id JOIN diets t ON t.id = m.diet_id) p "
            + "ON p.recipe_id = i.recipe_id AND r.library = FALSE "
            + "WHERE i.measure_picked = TRUE AND i.food_measure_id IS NOT NULL";

    private static final String EXTRAS = "SELECT x.id, x.unit, x.portion_size, x.food_measure_id, "
            + "x.composition_food_id, f.name_es, x.diet_id, t.reference_profile_code AS profile "
            + "FROM extra_foods x "
            + "JOIN composition_foods f ON f.id = x.composition_food_id "
            + "JOIN diets t ON t.id = x.diet_id "
            + "WHERE x.measure_picked = TRUE AND x.food_measure_id IS NOT NULL";

    private static final String RECIPE_INGREDIENTS = "recipe_ingredients";
    private static final String EXTRA_FOODS = "extra_foods";

    private static final int BATCH = 500;

    private final ReferenceMatcher matcher = new ReferenceMatcher();
    private final IFoodCategoriser categoriser = new FoodCategoriser();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Rules rules = rules(connection);
        for (Map.Entry<String, String> table : Map.of(RECIPE_INGREDIENTS, INGREDIENTS,
                EXTRA_FOODS, EXTRAS).entrySet()) {
            Map<Long, Boolean> agreed = agreed(connection, table.getValue(), rules);
            int freed = free(connection, table.getKey(), agreed);
            log.info("FD-054: {} with a stored measure: {}; the rule's (now free to follow criteria): {}; "
                    + "kept as picked: {}", table.getKey(), agreed.size(), freed, agreed.size() - freed);
        }
    }

    /** Every measure row, split the way the matcher is handed them, and each profile's source. */
    private static Rules rules(Connection connection) throws SQLException {
        ReferenceMapper mapper = new ReferenceMapper();
        List<FoodMeasureDto> published = new ArrayList<>();
        List<FoodMeasureDto> global = new ArrayList<>();
        Map<Long, List<FoodMeasureDto>> ownByDiet = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(MEASURES)) {
            while (result.next()) {
                FoodMeasureDto row = mapper.toDto(measureOf(result));
                if (row.dietId() != null) {
                    ownByDiet.computeIfAbsent(row.dietId(), diet -> new ArrayList<>()).add(row);
                } else if (row.globalOwn()) {
                    global.add(row);
                } else {
                    published.add(row);
                }
            }
        }
        Map<String, String> profileSources = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(PROFILE_SOURCES)) {
            while (result.next()) {
                profileSources.put(result.getString("code"), result.getString("source_code"));
            }
        }
        return new Rules(published, global, ownByDiet, profileSources);
    }

    /** A transient entity, so the mapper — and its gram-per-measure arithmetic — is the app's own. */
    private static ReferenceFoodMeasure measureOf(ResultSet result) throws SQLException {
        ReferenceFoodMeasure measure = new ReferenceFoodMeasure();
        measure.setId(result.getLong("id"));
        measure.setCode(result.getString("code"));
        long dietId = result.getLong("diet_id");
        measure.setDietId(result.wasNull() ? null : dietId);
        measure.setGlobalCriterion(result.getBoolean("global_criterion"));
        measure.setMeasure(HouseholdMeasure.valueOf(result.getString("measure")));
        measure.setSize(enumOf(PortionSize.class, result.getString("size")));
        measure.setCount(result.getBigDecimal("measure_count"));
        long foodId = result.getLong("composition_food_id");
        measure.setCompositionFoodId(result.wasNull() ? null : foodId);
        measure.setFoodCategory(enumOf(FoodCategory.class, result.getString("food_category")));
        measure.setKeywords(result.getString("keywords"));
        measure.setFoodLabel(result.getString("food_label"));
        measure.setGramsMin(result.getBigDecimal("grams_min"));
        measure.setGramsMax(result.getBigDecimal("grams_max"));
        measure.setMlMin(result.getBigDecimal("ml_min"));
        measure.setMlMax(result.getBigDecimal("ml_max"));
        measure.setState(FoodState.valueOf(result.getString("state")));
        measure.setWeightBasis(WeightBasis.valueOf(result.getString("weight_basis")));
        measure.setGrossGrams(result.getBigDecimal("gross_grams"));
        measure.setHouseholdText(result.getString("household_text"));
        measure.setPageRef(result.getString("page_ref"));
        measure.setNote(result.getString("note"));
        String sourceCode = result.getString("source_code");
        if (sourceCode != null) {
            ReferenceSource source = new ReferenceSource();
            source.setCode(sourceCode);
            source.setShortName(result.getString("source_short_name"));
            source.setTier(result.getInt("source_tier"));
            measure.setSource(source);
        }
        return measure;
    }

    /**
     * Each stored row with a picked measure, and whether the rule chooses that same
     * measure today. A row read twice (a private recipe served by two plates, which
     * nothing writes) is the rule's only when every reading agrees.
     */
    private Map<Long, Boolean> agreed(Connection connection, String sql, Rules rules) throws SQLException {
        Map<Long, Boolean> agreed = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                long dietId = result.getLong("diet_id");
                Long diet = result.wasNull() ? null : dietId;
                String nameEs = result.getString("name_es");
                MeasureQueryDto query = new MeasureQueryDto(result.getLong("composition_food_id"), nameEs,
                        result.getString("unit"), enumOf(PortionSize.class, result.getString("portion_size")),
                        null);
                MeasureChoiceDto choice = matcher.chooseMeasure(rules.published(),
                        diet == null ? List.of() : rules.ownByDiet().getOrDefault(diet, List.of()),
                        rules.global(), query, nameEs == null ? null : categoriser.of(nameEs),
                        rules.profileSources().get(result.getString("profile")));
                boolean same = choice.chosen() != null
                        && choice.chosen().id() == result.getLong("food_measure_id");
                agreed.merge(result.getLong("id"), same, Boolean::logicalAnd);
            }
        }
        return agreed;
    }

    private static int free(Connection connection, String table, Map<Long, Boolean> agreed)
            throws SQLException {
        int freed = 0;
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET measure_picked = FALSE WHERE id = ?")) {
            for (Map.Entry<Long, Boolean> row : agreed.entrySet()) {
                if (!row.getValue()) {
                    continue;
                }
                update.setLong(1, row.getKey());
                update.addBatch();
                if (++freed % BATCH == 0) {
                    update.executeBatch();
                }
            }
            update.executeBatch();
        }
        return freed;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name) {
        return name == null ? null : Enum.valueOf(type, name);
    }

    private record Rules(List<FoodMeasureDto> published, List<FoodMeasureDto> global,
                         Map<Long, List<FoodMeasureDto>> ownByDiet, Map<String, String> profileSources) {
    }
}
