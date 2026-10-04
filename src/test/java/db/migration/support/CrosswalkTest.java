package db.migration.support;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The crosswalk a Java migration matches through: the import's exact-name index, read by JDBC. */
class CrosswalkTest {

    private static final long LETTUCE = 1L;
    private static final long KIWI_CIQUAL = 2L;
    private static final long KIWI_BLS = 3L;

    @Test
    void answersSpanishNamesAndAliasesThePreferredRowWinningASharedName() throws SQLException {
        Crosswalk crosswalk = load(
                food(LETTUCE, "CIQUAL", "Lechuga", "lechuga romana;cogollo", false),
                food(KIWI_CIQUAL, "CIQUAL", "Kiwi", null, true),
                food(KIWI_BLS, "BLS", "Kiwi", null, false));

        assertThat(crosswalk.lookup(List.of("lechuga", "cogollo", "kiwi", "tomate")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "lechuga", LETTUCE, "cogollo", LETTUCE, "kiwi", KIWI_CIQUAL));
    }

    @Test
    void givesEachCrosswalkedFoodItsSpanishNameAndAnyOtherNone() throws SQLException {
        Crosswalk crosswalk = load(food(LETTUCE, "CIQUAL", "Lechuga", null, false));

        assertThat(crosswalk.spanishName(LETTUCE)).isEqualTo("Lechuga");
        assertThat(crosswalk.spanishName(99L)).isNull();
    }

    @SafeVarargs
    private static Crosswalk load(Map<String, Object>... foods) throws SQLException {
        return Crosswalk.load(FakeJdbc.connection(Map.of("FROM composition_foods", List.of(foods))));
    }

    private static Map<String, Object> food(long id, String source, String nameEs, String aliases,
                                            boolean preferred) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("source", source);
        row.put("source_code", "C" + id);
        row.put("name_es", nameEs);
        row.put("name_aliases", aliases);
        row.put("name_preferred", preferred);
        return row;
    }
}
