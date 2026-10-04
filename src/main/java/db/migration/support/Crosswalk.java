package db.migration.support;

import com.fdiet.food.dto.CompositionIndexRow;
import com.fdiet.food.helpers.NameIndex;
import com.fdiet.food.model.CompositionSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * fdiet's Spanish crosswalk on {@code composition_foods}, for a Java migration:
 * the exact-name index a fresh import matches through
 * ({@link CompositionIndexRow#nameIndex}) and each crosswalked food's Spanish
 * name, which the measure rule reads a food's family from. One select.
 *
 * <p>V18 carries an earlier copy of this select; it is an applied migration and
 * is left as it ran.
 */
public final class Crosswalk {

    private static final String CROSSWALKED_FOODS = "SELECT id, source, source_code, name_es, name_aliases, "
            + "name_preferred, name_en, name_original FROM composition_foods WHERE name_es IS NOT NULL";

    private final NameIndex<Long> names;
    private final Map<Long, String> spanishNames;

    private Crosswalk(NameIndex<Long> names, Map<Long, String> spanishNames) {
        this.names = names;
        this.spanishNames = spanishNames;
    }

    public static Crosswalk load(Connection connection) throws SQLException {
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
        Map<Long, String> spanishNames = new HashMap<>();
        rows.forEach(row -> spanishNames.put(row.id(), row.nameEs()));
        return new Crosswalk(CompositionIndexRow.nameIndex(rows), spanishNames);
    }

    /** The crosswalk's answer for names already normalised, keyed by them; O(n). */
    public Map<String, Long> lookup(Collection<String> wanted) {
        Map<String, Long> found = new HashMap<>();
        for (String name : wanted) {
            Long id = names.find(name);
            if (id != null) {
                found.put(name, id);
            }
        }
        return found;
    }

    /** The food's Spanish name, or null when the crosswalk names it in none. */
    public String spanishName(long compositionFoodId) {
        return spanishNames.get(compositionFoodId);
    }
}
