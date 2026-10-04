package db.migration;

import com.fdiet.diet.helpers.MealTextParser;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.PortionSize;
import db.migration.support.Crosswalk;
import db.migration.support.JdbcColumns;
import db.migration.support.MeasureRules;
import db.migration.support.RecipeReread;
import db.migration.support.RecipeReread.Outcome;
import db.migration.support.RecipeReread.RereadIngredient;
import db.migration.support.RecipeReread.SkippedRecipe;
import db.migration.support.RecipeReread.StoredIngredient;
import db.migration.support.RecipeReread.StoredRecipe;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FD-043: every recipe whose {@code raw_text} is known is read again with
 * today's {@link MealTextParser}, so a week stored by an older parser reads, and
 * matches, the way a fresh import of the same text would. Each ingredient takes
 * the re-read name, quantity, range, unit, state and size; a food already matched
 * and a measure a person picked are kept; an ingredient still unmatched is
 * matched by exact Spanish name or alias (V18's rule); a measure the rule chose
 * is chosen again by the publish rule. {@link RecipeReread} decides all of that;
 * this class only reads and writes.
 *
 * <p>Left untouched, and logged: a recipe without {@code raw_text} (nothing to
 * read again — the text is never rebuilt from the parts), and a recipe whose text
 * now reads into a different number of ingredients, since its rows can no longer
 * be paired with the text by position.
 *
 * <p>A private recipe is weighed inside the diet that serves it (that diet's
 * criteria and profile), a library recipe inside none, as V20 and a publish do.
 *
 * <p>Why a migration, like V18 and V20: it must run exactly once per database,
 * before anybody reads a week again, and Flyway's history is the record that it
 * did. On a fresh database it finds no rows.
 *
 * <p>Six selects, whatever the number of recipes, then one batched update: O(m)
 * over the measure rows and O(n) over the ingredient rows.
 */
public class V21__reread_recipe_texts extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V21__reread_recipe_texts.class);

    private static final String RECIPES_WITH_TEXT =
            "SELECT id, raw_text FROM recipes WHERE raw_text IS NOT NULL ORDER BY id";

    private static final String RECIPES_WITHOUT_TEXT = "SELECT COUNT(*) FROM recipes WHERE raw_text IS NULL";

    private static final String INGREDIENTS = "SELECT i.id, i.recipe_id, i.raw_name, i.quantity, "
            + "i.quantity_max, i.unit, i.state, i.portion_size, i.food_item_id, i.composition_food_id, "
            + "i.food_measure_id, i.measure_picked FROM recipe_ingredients i "
            + "JOIN recipes r ON r.id = i.recipe_id WHERE r.raw_text IS NOT NULL "
            + "ORDER BY i.recipe_id, i.position";

    /** The diet that serves each private recipe; a library recipe is in none. */
    private static final String PRIVATE_RECIPE_DIETS = "SELECT DISTINCT d.recipe_id, m.diet_id, "
            + "t.reference_profile_code AS profile FROM diet_dishes d "
            + "JOIN recipes r ON r.id = d.recipe_id "
            + "JOIN diet_meals m ON m.id = d.meal_id JOIN diets t ON t.id = m.diet_id "
            + "WHERE r.library = FALSE AND r.raw_text IS NOT NULL";

    private static final String UPDATE = "UPDATE recipe_ingredients SET raw_name = ?, quantity = ?, "
            + "quantity_max = ?, unit = ?, state = ?, portion_size = ?, composition_food_id = ?, "
            + "food_measure_id = ?, measure_picked = ? WHERE id = ?";

    private static final int BATCH = 500;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Crosswalk crosswalk = Crosswalk.load(connection);
        MeasureRules rules = MeasureRules.load(connection);
        Map<Long, DietContext> diets = privateRecipeDiets(connection);

        RecipeReread.MeasureRule rule = (foodId, unit, size, keep, recipeId) -> {
            DietContext diet = diets.getOrDefault(recipeId, DietContext.LIBRARY);
            return rules.choose(foodId, crosswalk.spanishName(foodId), unit, size, keep,
                    diet.dietId(), diet.profile());
        };
        Outcome outcome = RecipeReread.reread(storedRecipes(connection), new MealTextParser(),
                crosswalk::lookup, rule);
        write(connection, outcome.changed());

        log.info("FD-043: ingredients re-read: {}; rows changed: {}; re-matched by exact Spanish name: {}; "
                        + "measures chosen: {}; released: {}; recipes without raw_text, untouched: {}",
                outcome.reread(), outcome.changed().size(), outcome.rematched(), outcome.measuresChosen(),
                outcome.measuresReleased(), recipesWithoutText(connection));
        log.info("FD-043: recipes left untouched because their text reads into another number of "
                + "ingredients: {}", outcome.skipped().size());
        for (SkippedRecipe skipped : outcome.skipped()) {
            log.info("FD-043: recipe {} untouched: {} stored, {} read from \"{}\"",
                    skipped.id(), skipped.stored(), skipped.read(), skipped.rawText());
        }
    }

    private static List<StoredRecipe> storedRecipes(Connection connection) throws SQLException {
        Map<Long, String> texts = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(RECIPES_WITH_TEXT)) {
            while (result.next()) {
                texts.put(result.getLong("id"), result.getString("raw_text"));
            }
        }
        Map<Long, List<StoredIngredient>> byRecipe = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(INGREDIENTS)) {
            while (result.next()) {
                byRecipe.computeIfAbsent(result.getLong("recipe_id"), recipe -> new ArrayList<>())
                        .add(ingredientOf(result));
            }
        }
        List<StoredRecipe> recipes = new ArrayList<>(texts.size());
        texts.forEach((id, text) -> recipes.add(
                new StoredRecipe(id, text, byRecipe.getOrDefault(id, List.of()))));
        return recipes;
    }

    private static StoredIngredient ingredientOf(ResultSet result) throws SQLException {
        return new StoredIngredient(
                result.getLong("id"),
                result.getString("raw_name"),
                result.getBigDecimal("quantity"),
                result.getBigDecimal("quantity_max"),
                result.getString("unit"),
                JdbcColumns.enumOf(FoodState.class, result.getString("state")),
                JdbcColumns.enumOf(PortionSize.class, result.getString("portion_size")),
                JdbcColumns.longOrNull(result, "food_item_id"),
                JdbcColumns.longOrNull(result, "composition_food_id"),
                JdbcColumns.longOrNull(result, "food_measure_id"),
                result.getBoolean("measure_picked"));
    }

    /** A private recipe is served by one plate; should two serve it, the first diet read weighs it. */
    private static Map<Long, DietContext> privateRecipeDiets(Connection connection) throws SQLException {
        Map<Long, DietContext> diets = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(PRIVATE_RECIPE_DIETS)) {
            while (result.next()) {
                diets.putIfAbsent(result.getLong("recipe_id"),
                        new DietContext(result.getLong("diet_id"), result.getString("profile")));
            }
        }
        return diets;
    }

    private static long recipesWithoutText(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(RECIPES_WITHOUT_TEXT)) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private static void write(Connection connection, List<RereadIngredient> rows) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(UPDATE)) {
            int pending = 0;
            for (RereadIngredient row : rows) {
                update.setString(1, row.rawName());
                update.setBigDecimal(2, row.quantity());
                update.setBigDecimal(3, row.quantityMax());
                update.setString(4, row.unit());
                update.setString(5, row.state() == null ? null : row.state().name());
                update.setString(6, row.size() == null ? null : row.size().name());
                setLong(update, 7, row.compositionFoodId());
                setLong(update, 8, row.foodMeasureId());
                update.setBoolean(9, row.measurePicked());
                update.setLong(10, row.id());
                update.addBatch();
                if (++pending % BATCH == 0) {
                    update.executeBatch();
                }
            }
            update.executeBatch();
        }
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private record DietContext(Long dietId, String profile) {
        static final DietContext LIBRARY = new DietContext(null, null);
    }
}
