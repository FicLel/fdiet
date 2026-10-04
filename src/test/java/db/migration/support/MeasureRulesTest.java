package db.migration.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The migrations' copy of the publish rule: rows loaded by JDBC and split the way
 * {@code ReferenceService} hands them to the matcher, so the precedence a publish
 * applies — picked, diet criterion, global criterion, published — holds here too.
 */
class MeasureRulesTest {

    private static final long OIL = 10L;
    private static final long BUTTER = 11L;
    private static final long HONEY = 12L;
    private static final long OLIVE = 13L;
    private static final long DIET = 7L;
    private static final String ADULT = "ADULT";
    private static final String AESAN = "AESAN-2022";

    private static final long PUBLISHED_OIL = 1L;
    private static final long DIET_OIL = 2L;
    private static final long GLOBAL_OIL = 3L;
    private static final long PUBLISHED_BUTTER = 4L;
    private static final long HONEY_AESAN = 5L;
    private static final long HONEY_OTHER = 6L;
    private static final long FAMILY_FAT = 7L;

    private MeasureRules rules;

    @BeforeEach
    void load() throws SQLException {
        rules = MeasureRules.load(FakeJdbc.connection(Map.of(
                "FROM ref_food_measures", List.of(
                        teaspoon(PUBLISHED_OIL, OIL, 5, AESAN),
                        criterion(DIET_OIL, OIL, 4, DIET, false),
                        criterion(GLOBAL_OIL, OIL, 6, null, true),
                        teaspoon(PUBLISHED_BUTTER, BUTTER, 5, AESAN),
                        teaspoon(HONEY_AESAN, HONEY, 7, AESAN),
                        teaspoon(HONEY_OTHER, HONEY, 8, "OTHER"),
                        family(FAMILY_FAT, "FAT_OIL", 4.5, AESAN)),
                "FROM ref_populations", List.of(Map.of("code", ADULT, "source_code", AESAN)))));
    }

    @Test
    void aDietsCriterionBeatsTheGlobalOneAndThePublishedRow() {
        assertThat(rules.choose(OIL, null, "cdta", null, null, DIET, ADULT)).isEqualTo(DIET_OIL);
    }

    /** A library recipe is weighed inside no diet: the global criterion decides. */
    @Test
    void aLibraryRecipeReadsNoDietsCriterion() {
        assertThat(rules.choose(OIL, null, "cdta", null, null, null, ADULT)).isEqualTo(GLOBAL_OIL);
    }

    @Test
    void aPickStillFittingBeatsEveryCriterion() {
        assertThat(rules.choose(OIL, null, "cdta", null, PUBLISHED_OIL, DIET, ADULT)).isEqualTo(PUBLISHED_OIL);
    }

    @Test
    void aPickForAnotherWordGivesWayToTheRule() {
        assertThat(rules.choose(OIL, null, "taza", null, PUBLISHED_OIL, DIET, ADULT)).isNull();
    }

    @Test
    void aDietsCriterionForAnotherFoodDoesNotReachThisOne() {
        assertThat(rules.choose(BUTTER, null, "cdta", null, null, DIET, ADULT)).isEqualTo(PUBLISHED_BUTTER);
    }

    /** Two published rows that disagree: the profile's source decides, and without a profile nobody does. */
    @Test
    void theProfilesSourceSettlesPublishedRowsThatDisagree() {
        assertThat(rules.choose(HONEY, null, "cdta", null, null, null, ADULT)).isEqualTo(HONEY_AESAN);
        assertThat(rules.choose(HONEY, null, "cdta", null, null, null, null)).isNull();
    }

    /** A family row reaches a food through its Spanish name only. */
    @Test
    void aFamilyRowReachesAFoodByItsSpanishName() {
        assertThat(rules.choose(OLIVE, "Aceite de girasol", "cdta", null, null, null, ADULT))
                .isEqualTo(FAMILY_FAT);
        assertThat(rules.choose(OLIVE, null, "cdta", null, null, null, ADULT)).isNull();
    }

    @Test
    void aWeightOrAVolumeNeedsNoMeasure() {
        assertThat(rules.choose(OIL, null, "g", null, null, DIET, ADULT)).isNull();
        assertThat(rules.choose(OIL, null, "ml", null, PUBLISHED_OIL, DIET, ADULT)).isNull();
        assertThat(rules.choose(OIL, null, null, null, null, DIET, ADULT)).isNull();
    }

    private static Map<String, Object> teaspoon(long id, long food, double grams, String source) {
        Map<String, Object> row = row(id, grams);
        row.put("composition_food_id", food);
        row.put("code", "M" + id);
        row.put("source_code", source);
        row.put("source_short_name", source);
        row.put("source_tier", 1);
        return row;
    }

    private static Map<String, Object> family(long id, String category, double grams, String source) {
        Map<String, Object> row = row(id, grams);
        row.put("food_category", category);
        row.put("code", "M" + id);
        row.put("source_code", source);
        row.put("source_short_name", source);
        row.put("source_tier", 1);
        return row;
    }

    private static Map<String, Object> criterion(long id, long food, double grams, Long diet, boolean global) {
        Map<String, Object> row = row(id, grams);
        row.put("composition_food_id", food);
        row.put("diet_id", diet);
        row.put("global_criterion", global);
        return row;
    }

    private static Map<String, Object> row(long id, double grams) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("global_criterion", false);
        row.put("measure", "CUCHARADITA");
        row.put("measure_count", 1);
        row.put("grams_min", grams);
        row.put("grams_max", grams);
        row.put("state", "UNSPECIFIED");
        row.put("weight_basis", "NET_EDIBLE");
        return row;
    }
}
