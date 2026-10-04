package db.migration;

import com.fdiet.common.helper.Texts;
import com.fdiet.diet.helpers.ExactNames;
import com.fdiet.food.dto.CompositionIndexRow;
import com.fdiet.food.helpers.NameIndex;
import com.fdiet.food.model.CompositionSource;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FD-033 phase D, the second half of V17: the ingredients and extras V17 reset are
 * matched again, by <strong>exact Spanish name or alias only</strong>, through
 * fdiet's crosswalk on {@code composition_foods} (decision 19).
 *
 * <p>It is the rule a fresh import goes through — {@link ExactNames} over the
 * name index {@link CompositionIndexRow#nameIndex} builds, the same two pieces
 * {@code FoodResolverService} and {@code CompositionFoodService} use — so a stored
 * week ends up matched exactly as it would be if it were imported again today.
 * Never by similarity, and never by carrying the old BEDCA id over: anything not
 * exact stays unmatched and the fix-up list offers it suggestions.
 *
 * <p>Why a migration rather than a startup step or an endpoint: it has to run
 * exactly once per database, right after the reset and before anybody reads a
 * week, and Flyway's history is the record that it did. A startup step would need
 * a marker of its own, and an endpoint would stay behind able to re-match archived
 * weeks silently whenever it was called. On a fresh database it finds no rows and
 * changes nothing; on one whose crosswalk is not loaded yet it finds no names and
 * leaves everything unmatched, which the fix-up list then shows.
 *
 * <p>Branded matches ({@code food_item_id}) are left alone. A household measure
 * attached to an ingredient or extra is released when its row names a composition
 * food other than the one now matched; the resolver chooses again on the next
 * save. Plain JDBC: no bean exists yet while Flyway runs.
 *
 * <p>O(n) over the composition rows and the stored rows; three selects, one batched
 * update per table and one measure update per table.
 */
public class V18__rematch_ingredients_by_composition_name extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V18__rematch_ingredients_by_composition_name.class);

    private static final String RECIPE_INGREDIENTS = "recipe_ingredients";
    private static final String EXTRA_FOODS = "extra_foods";

    private static final String CROSSWALKED_FOODS = "SELECT id, source, source_code, name_es, name_aliases, "
            + "name_preferred, name_en, name_original FROM composition_foods WHERE name_es IS NOT NULL";

    private static final int BATCH = 500;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        NameIndex<Long> names = CompositionIndexRow.nameIndex(crosswalkedFoods(connection));
        for (String table : List.of(RECIPE_INGREDIENTS, EXTRA_FOODS)) {
            int matched = rematch(connection, table, names);
            int released = releaseMeasuresOfOtherFoods(connection, table);
            log.info("FD-033 D: {} re-matched by exact Spanish name: {}; household measures released: {}",
                    table, matched, released);
        }
    }

    private static List<CompositionIndexRow> crosswalkedFoods(Connection connection) throws SQLException {
        List<CompositionIndexRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(CROSSWALKED_FOODS)) {
            while (result.next()) {
                rows.add(new CompositionIndexRow(
                        result.getLong("id"),
                        CompositionSource.valueOf(result.getString("source")),
                        result.getString("source_code"),
                        result.getString("name_es"),
                        result.getString("name_aliases"),
                        result.getBoolean("name_preferred"),
                        result.getString("name_en"),
                        result.getString("name_original")));
            }
        }
        return rows;
    }

    /** Every row with no food, its name put to the crosswalk; answers how many found one. */
    private static int rematch(Connection connection, String table, NameIndex<Long> names)
            throws SQLException {
        Map<Long, String> unmatched = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, raw_name FROM " + table
                     + " WHERE food_item_id IS NULL AND composition_food_id IS NULL")) {
            while (result.next()) {
                unmatched.put(result.getLong("id"), result.getString("raw_name"));
            }
        }
        Map<String, Long> found = ExactNames.resolve(unmatched.values(), wanted -> lookup(names, wanted));

        int matched = 0;
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET composition_food_id = ? WHERE id = ?")) {
            for (Map.Entry<Long, String> row : unmatched.entrySet()) {
                Long foodId = found.get(Texts.normaliseName(row.getValue()));
                if (foodId == null) {
                    continue;
                }
                update.setLong(1, foodId);
                update.setLong(2, row.getKey());
                update.addBatch();
                if (++matched % BATCH == 0) {
                    update.executeBatch();
                }
            }
            update.executeBatch();
        }
        return matched;
    }

    /** The crosswalk's answer for names already normalised, keyed by them. */
    private static Map<String, Long> lookup(NameIndex<Long> names, Collection<String> wanted) {
        Map<String, Long> found = new HashMap<>();
        for (String name : wanted) {
            Long id = names.find(name);
            if (id != null) {
                found.put(name, id);
            }
        }
        return found;
    }

    /**
     * A measure row naming one composition food weighs nothing else: attached to a
     * row now matched to another food, or to none, it is released. A family row
     * stays, and is checked again by the resolver on the next save.
     */
    private static int releaseMeasuresOfOtherFoods(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate("UPDATE " + table + " x JOIN ref_food_measures m "
                    + "ON m.id = x.food_measure_id SET x.food_measure_id = NULL "
                    + "WHERE m.composition_food_id IS NOT NULL "
                    + "AND NOT (m.composition_food_id <=> x.composition_food_id)");
        }
    }
}
