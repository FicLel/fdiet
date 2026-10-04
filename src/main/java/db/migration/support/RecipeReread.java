package db.migration.support;

import com.fdiet.common.helper.Texts;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.RecipeDto;
import com.fdiet.diet.helpers.ExactNames;
import com.fdiet.diet.helpers.IMealTextParser;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.PortionSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * FD-043: a stored recipe read again from its {@code raw_text} with today's
 * parser, the way a fresh import would read it, without losing what a person or
 * an earlier migration already settled.
 *
 * <p>The re-read ingredients are paired with the stored ones <strong>by
 * position</strong>: the n-th fragment of the text is the n-th stored row, which
 * is how the importer wrote them. Names cannot pair them, because the name is
 * exactly what changes ({@code 1 infusión sin azúcar} becomes
 * {@code infusión sin azúcar}). A recipe whose text now reads into a different
 * number of ingredients cannot be paired at all and is left as it is, listed.
 *
 * <p>Per pair, the row takes the re-read name, quantity, range, unit, state and
 * size, and:
 * <ul>
 *   <li>keeps its food — composition or branded — whatever the new name would
 *       match (FD-048: a match already made beats the exact-name rule);</li>
 *   <li>when it had none, is matched by exact Spanish name or alias, once more
 *       without size words ({@link ExactNames}, V18's rule and the import's);</li>
 *   <li>keeps a measure a person picked while it still measures the re-read unit
 *       and covers the food, as a publish re-validates a pick (FD-039);
 *       otherwise the rule decides;</li>
 *   <li>has a measure the rule chose — or no measure at all — chosen again by the
 *       publish rule, which may attach one, change it or release it (FD-054).</li>
 * </ul>
 * So a re-read row carries the measure the next publish would give it.
 * A branded match is left with its measure as it was: measures weigh composition
 * foods only.
 *
 * <p>Pure: the parser, the name lookup and the measure rule are handed in, so the
 * migration reads the database and this decides. O(n) over the stored rows, with
 * the name lookup asked twice at most for all recipes together.
 */
public final class RecipeReread {

    private RecipeReread() {
    }

    /**
     * The publish rule's measure for one row, or null when it attaches none.
     * {@code keep} is a measure to keep while it still fits, as a pick is kept.
     */
    @FunctionalInterface
    public interface MeasureRule {
        Long choose(long compositionFoodId, String unit, PortionSize size, Long keep, long recipeId);
    }

    /** A stored recipe with its ingredients in position order. */
    public record StoredRecipe(long id, String rawText, List<StoredIngredient> ingredients) {
    }

    /** One {@code recipe_ingredients} row as stored. */
    public record StoredIngredient(long id, String rawName, BigDecimal quantity, BigDecimal quantityMax,
                                   String unit, FoodState state, PortionSize size, Long foodItemId,
                                   Long compositionFoodId, Long foodMeasureId, boolean measurePicked) {

        /** The flag means something only beside a measure (see {@code RecipeIngredient}). */
        public boolean picked() {
            return measurePicked && foodMeasureId != null;
        }

        boolean matched() {
            return foodItemId != null || compositionFoodId != null;
        }
    }

    /** What a row becomes; {@code food_item_id} is never written, so it is not here. */
    public record RereadIngredient(long id, String rawName, BigDecimal quantity, BigDecimal quantityMax,
                                   String unit, FoodState state, PortionSize size, Long compositionFoodId,
                                   Long foodMeasureId, boolean measurePicked) {
    }

    /** A recipe left untouched because its text reads into another number of ingredients. */
    public record SkippedRecipe(long id, String rawText, int stored, int read) {
    }

    /**
     * Everything the re-read decided: the rows that change, the recipes left
     * alone, and how many rows each step touched.
     */
    public record Outcome(List<RereadIngredient> changed, List<SkippedRecipe> skipped, int reread,
                          int rematched, int measuresChosen, int measuresReleased) {
    }

    /**
     * Reads every recipe again and decides each row.
     *
     * @param names answers names already normalised ({@link Texts#normaliseName}),
     *              keyed by those names — the crosswalk's index
     */
    public static Outcome reread(List<StoredRecipe> recipes, IMealTextParser parser,
                                 Function<Collection<String>, Map<String, Long>> names,
                                 MeasureRule rule) {
        List<Pair> pairs = new ArrayList<>();
        List<SkippedRecipe> skipped = new ArrayList<>();
        for (StoredRecipe recipe : recipes) {
            RecipeDto read = parser.parse(recipe.rawText(), null);
            List<DishIngredient> ingredients = read == null ? List.of() : read.ingredients();
            if (ingredients.size() != recipe.ingredients().size()) {
                skipped.add(new SkippedRecipe(recipe.id(), recipe.rawText(),
                        recipe.ingredients().size(), ingredients.size()));
                continue;
            }
            for (int at = 0; at < ingredients.size(); at++) {
                pairs.add(new Pair(recipe.id(), recipe.ingredients().get(at), ingredients.get(at)));
            }
        }

        Map<String, Long> found = ExactNames.resolve(pairs.stream()
                .filter(pair -> !pair.stored().matched())
                .map(pair -> pair.read().name())
                .toList(), names);

        List<RereadIngredient> changed = new ArrayList<>();
        Counts counts = new Counts();
        for (Pair pair : pairs) {
            RereadIngredient row = decide(pair, found, rule, counts);
            if (!sameAs(row, pair.stored())) {
                changed.add(row);
            }
        }
        return new Outcome(List.copyOf(changed), List.copyOf(skipped), pairs.size(),
                counts.rematched, counts.chosen, counts.released);
    }

    private static RereadIngredient decide(Pair pair, Map<String, Long> found, MeasureRule rule,
                                           Counts counts) {
        StoredIngredient stored = pair.stored();
        DishIngredient read = pair.read();
        Long foodId = stored.compositionFoodId();
        if (!stored.matched()) {
            foodId = found.get(Texts.normaliseName(read.name()));
            if (foodId != null) {
                counts.rematched++;
            }
        }
        Measure measure = measureOf(pair, foodId, rule);
        if (measure.id() != null && !measure.id().equals(stored.foodMeasureId())) {
            counts.chosen++;
        } else if (measure.id() == null && stored.foodMeasureId() != null) {
            counts.released++;
        }
        return new RereadIngredient(stored.id(), read.name(), read.quantity(), read.quantityMax(),
                read.unit(), read.state(), read.size(), foodId, measure.id(), measure.picked());
    }

    /**
     * The measure a publish would attach to the re-read row ({@code DietMapper}
     * over {@code MeasureResolverService}): a pick is handed to the rule as the
     * measure to keep, so it survives while it still measures the unit and covers
     * the food and gives way to the rule's choice otherwise (FD-039); a measure the
     * rule chose, or none, is chosen again from scratch (FD-054).
     */
    private static Measure measureOf(Pair pair, Long foodId, MeasureRule rule) {
        StoredIngredient stored = pair.stored();
        DishIngredient read = pair.read();
        if (stored.foodItemId() != null) {
            return new Measure(stored.foodMeasureId(), stored.picked());
        }
        if (foodId == null) {
            return Measure.NONE;
        }
        Long pick = stored.picked() ? stored.foodMeasureId() : null;
        Long chosen = rule.choose(foodId, read.unit(), read.size(), pick, pair.recipeId());
        return new Measure(chosen, chosen != null && chosen.equals(pick));
    }

    private static boolean sameAs(RereadIngredient row, StoredIngredient stored) {
        return row.rawName().equals(stored.rawName())
                && sameNumber(row.quantity(), stored.quantity())
                && sameNumber(row.quantityMax(), stored.quantityMax())
                && row.unit().equals(stored.unit())
                && row.state() == stored.state()
                && row.size() == stored.size()
                && Objects.equals(row.compositionFoodId(), stored.compositionFoodId())
                && Objects.equals(row.foodMeasureId(), stored.foodMeasureId())
                && row.measurePicked() == stored.picked();
    }

    /** {@code 80} and {@code 80.00} are the same quantity; the column's scale is not. */
    private static boolean sameNumber(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private record Pair(long recipeId, StoredIngredient stored, DishIngredient read) {
    }

    private record Measure(Long id, boolean picked) {
        static final Measure NONE = new Measure(null, false);
    }

    private static final class Counts {
        int rematched;
        int chosen;
        int released;
    }
}
