package db.migration.support;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.alternative.helpers.FoodCategoriser;
import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.diet.helpers.PortionScaler;
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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The publish rule for choosing a household measure, for a Java migration: every
 * measure row read once and split the way {@code ReferenceService} hands them to
 * {@link ReferenceMatcher#chooseMeasure} — published rows, each diet's own
 * criteria, the global criteria — plus each profile's source. Plain JDBC, because
 * no bean exists while Flyway runs; the mapper and the matcher are the app's own,
 * so the arithmetic and the precedence cannot drift from a publish.
 *
 * <p>V20 carries an earlier copy of this loading; it is an applied migration and
 * is left as it ran.
 *
 * <p>Two selects, then every choice in memory: O(m) to load the m measure rows,
 * O(m) per choice over the rows of one measure word.
 */
public final class MeasureRules {

    private static final String MEASURES = "SELECT m.id, m.code, m.diet_id, m.global_criterion, "
            + "m.measure, m.size, m.measure_count, m.composition_food_id, m.food_category, "
            + "m.keywords, m.food_label, m.grams_min, m.grams_max, m.ml_min, m.ml_max, m.state, "
            + "m.weight_basis, m.gross_grams, m.household_text, m.page_ref, m.note, "
            + "s.code AS source_code, s.short_name AS source_short_name, s.tier AS source_tier "
            + "FROM ref_food_measures m LEFT JOIN ref_sources s ON s.id = m.source_id ORDER BY m.id";

    private static final String PROFILE_SOURCES = "SELECT p.code, s.code AS source_code "
            + "FROM ref_populations p JOIN ref_sources s ON s.id = p.source_id";

    private final ReferenceMatcher matcher = new ReferenceMatcher();
    private final IFoodCategoriser categoriser = new FoodCategoriser();

    private final List<FoodMeasureDto> published;
    private final List<FoodMeasureDto> global;
    private final Map<Long, List<FoodMeasureDto>> ownByDiet;
    private final Map<String, String> profileSources;

    private MeasureRules(List<FoodMeasureDto> published, List<FoodMeasureDto> global,
                         Map<Long, List<FoodMeasureDto>> ownByDiet, Map<String, String> profileSources) {
        this.published = published;
        this.global = global;
        this.ownByDiet = ownByDiet;
        this.profileSources = profileSources;
    }

    /** Every measure row and every profile's source, in two selects. */
    public static MeasureRules load(Connection connection) throws SQLException {
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
        return new MeasureRules(published, global, ownByDiet, profileSources);
    }

    /**
     * The measure the publish rule attaches, or null when it attaches none — a
     * weight or a volume needs none, and a judgement is left to a person.
     *
     * @param nameEs the food's Spanish name, null when the crosswalk names it in none
     * @param keep   a measure kept while it still fits the food and unit, as a pick is
     * @param dietId the diet whose own criteria apply, null for a library recipe
     * @param profile the diet's reference profile code, or null
     */
    public Long choose(long compositionFoodId, String nameEs, String unit, PortionSize size, Long keep,
                       Long dietId, String profile) {
        if (unit == null || PortionScaler.isWeightOrVolume(unit)) {
            return null;
        }
        MeasureQueryDto query = new MeasureQueryDto(compositionFoodId, nameEs, unit, size, keep);
        FoodCategory category = nameEs == null ? null : categoriser.of(nameEs);
        MeasureChoiceDto choice = matcher.chooseMeasure(published,
                dietId == null ? List.of() : ownByDiet.getOrDefault(dietId, List.of()),
                global, query, category, profileSources.get(profile));
        return choice.chosen() == null ? null : choice.chosen().id();
    }

    /** A transient entity, so the mapper — and its gram-per-measure arithmetic — is the app's own. */
    private static ReferenceFoodMeasure measureOf(ResultSet result) throws SQLException {
        ReferenceFoodMeasure measure = new ReferenceFoodMeasure();
        measure.setId(result.getLong("id"));
        measure.setCode(result.getString("code"));
        measure.setDietId(JdbcColumns.longOrNull(result, "diet_id"));
        measure.setGlobalCriterion(result.getBoolean("global_criterion"));
        measure.setMeasure(HouseholdMeasure.valueOf(result.getString("measure")));
        measure.setSize(JdbcColumns.enumOf(PortionSize.class, result.getString("size")));
        measure.setCount(result.getBigDecimal("measure_count"));
        measure.setCompositionFoodId(JdbcColumns.longOrNull(result, "composition_food_id"));
        measure.setFoodCategory(JdbcColumns.enumOf(FoodCategory.class, result.getString("food_category")));
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
}
