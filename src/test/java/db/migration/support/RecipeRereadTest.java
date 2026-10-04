package db.migration.support;

import com.fdiet.diet.helpers.MealTextParser;
import com.fdiet.reference.domain.PortionSize;
import db.migration.support.RecipeReread.Outcome;
import db.migration.support.RecipeReread.RereadIngredient;
import db.migration.support.RecipeReread.StoredIngredient;
import db.migration.support.RecipeReread.StoredRecipe;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FD-043: a stored recipe read again from its text, with the real parser, a fake
 * crosswalk and a fake measure rule. What a person or an earlier match settled
 * survives; what the older parser read badly is read again.
 */
class RecipeRereadTest {

    private static final long AOVE = 10L;
    private static final long INFUSION = 11L;
    private static final long KIWI = 12L;
    private static final long OTHER_FOOD = 13L;

    private static final long TEASPOON_RULE = 100L;
    private static final long PICKED_MEASURE = 101L;
    private static final long OLD_RULE_MEASURE = 102L;

    private static final Map<String, Long> CROSSWALK = Map.of(
            "aove", AOVE, "infusión sin azúcar", INFUSION, "kiwi", KIWI, "1 kiwi", OTHER_FOOD);

    private final MealTextParser parser = new MealTextParser();
    private final List<Collection<String>> asked = new ArrayList<>();

    /**
     * The rule as a publish applies it: a measure handed to keep is kept while it
     * still fits (here, while it is in {@code fits}); otherwise a teaspoon is weighed
     * by the teaspoon row, or by {@code criterion} once one is written.
     */
    private final Set<Long> fits = new HashSet<>();
    private Long criterion;
    private final RecipeReread.MeasureRule rule = (food, unit, size, keep, recipe) -> {
        if (keep != null && fits.contains(keep)) {
            return keep;
        }
        if (!"cdta".equals(unit)) {
            return null;
        }
        return criterion != null ? criterion : TEASPOON_RULE;
    };

    @Test
    void readsTheOlderNamesAgainAndMatchesThemByExactName() {
        Outcome outcome = reread(recipe(1, "Ensalada: 1 cdta AOVE + 1 infusión sin azúcar",
                unmatched(1, "1 cdta AOVE", "1", "unidad"),
                unmatched(2, "1 infusión sin azúcar", "1", "unidad")));

        assertThat(outcome.changed()).hasSize(2);
        RereadIngredient oil = outcome.changed().get(0);
        assertThat(oil.rawName()).isEqualTo("AOVE");
        assertThat(oil.unit()).isEqualTo("cdta");
        assertThat(oil.compositionFoodId()).isEqualTo(AOVE);
        assertThat(oil.foodMeasureId()).isEqualTo(TEASPOON_RULE);
        assertThat(oil.measurePicked()).isFalse();
        assertThat(outcome.changed().get(1).compositionFoodId()).isEqualTo(INFUSION);
        assertThat(outcome.rematched()).isEqualTo(2);
        assertThat(outcome.measuresChosen()).isEqualTo(1);
        assertThat(asked).hasSize(1);
    }

    /** A match already made beats the exact-name rule, even when the new name names another food. */
    @Test
    void keepsAFoodAlreadyMatched() {
        Outcome outcome = reread(recipe(1, "1 kiwi",
                stored(1, "kiwi entero", "1", "unidad", OTHER_FOOD, null, false)));

        RereadIngredient kiwi = outcome.changed().get(0);
        assertThat(kiwi.rawName()).isEqualTo("kiwi");
        assertThat(kiwi.compositionFoodId()).isEqualTo(OTHER_FOOD);
        assertThat(outcome.rematched()).isZero();
        assertThat(asked).isEmpty();
    }

    @Test
    void keepsAPickedMeasureWhileItStillFitsTheRereadUnit() {
        fits.add(PICKED_MEASURE);

        Outcome outcome = reread(recipe(1, "1 cdta AOVE",
                stored(1, "1 cdta AOVE", "1", "unidad", AOVE, PICKED_MEASURE, true)));

        RereadIngredient oil = outcome.changed().get(0);
        assertThat(oil.unit()).isEqualTo("cdta");
        assertThat(oil.foodMeasureId()).isEqualTo(PICKED_MEASURE);
        assertThat(oil.measurePicked()).isTrue();
    }

    /** FD-039, as a publish re-validates a pick: one that no longer fits gives way to the rule. */
    @Test
    void dropsAPickThatNoLongerFitsForTheRulesChoice() {
        Outcome outcome = reread(recipe(1, "1 cdta AOVE",
                stored(1, "1 cdta AOVE", "1", "unidad", AOVE, PICKED_MEASURE, true)));

        RereadIngredient oil = outcome.changed().get(0);
        assertThat(oil.foodMeasureId()).isEqualTo(TEASPOON_RULE);
        assertThat(oil.measurePicked()).isFalse();
    }

    /** FD-054: the rule's measure is chosen afresh even when unit, size and food are unchanged. */
    @Test
    void followsTheRuleWhenNothingWrittenChanged() {
        criterion = OLD_RULE_MEASURE;
        fits.add(TEASPOON_RULE);

        Outcome outcome = reread(recipe(1, "AOVE (1 cdta) + AOVE (2 cdta)",
                stored(1, "AOVE", "1", "cdta", AOVE, TEASPOON_RULE, false),
                stored(2, "AOVE", "2", "cdta", AOVE, null, false)));

        assertThat(outcome.changed()).extracting(RereadIngredient::id, RereadIngredient::foodMeasureId,
                        RereadIngredient::measurePicked)
                .containsExactly(Tuple.tuple(1L, OLD_RULE_MEASURE, false),
                        Tuple.tuple(2L, OLD_RULE_MEASURE, false));
        assertThat(outcome.measuresChosen()).isEqualTo(2);
    }

    @Test
    void choosesTheRulesMeasureAgainWhenTheUnitChanges() {
        Outcome outcome = reread(recipe(1, "1 cdta AOVE",
                stored(1, "1 cdta AOVE", "1", "unidad", AOVE, OLD_RULE_MEASURE, false)));

        assertThat(outcome.changed().get(0).foodMeasureId()).isEqualTo(TEASPOON_RULE);
        assertThat(outcome.measuresChosen()).isEqualTo(1);
    }

    @Test
    void releasesTheRulesMeasureWhenItNoLongerWeighs() {
        Outcome outcome = reread(recipe(1, "kiwi (2 unidad) + AOVE (1 cdta)",
                stored(1, "kiwi", "2", "unidad", KIWI, OLD_RULE_MEASURE, false),
                stored(2, "AOVE", "1", "cdta", AOVE, TEASPOON_RULE, false)));

        // The oil's measure still weighs and is kept; the kiwi's no longer does.
        assertThat(outcome.changed()).singleElement().satisfies(kiwi -> {
            assertThat(kiwi.id()).isEqualTo(1);
            assertThat(kiwi.foodMeasureId()).isNull();
            assertThat(kiwi.measurePicked()).isFalse();
        });
        assertThat(outcome.measuresReleased()).isEqualTo(1);
    }

    @Test
    void leavesARecipeWhoseTextReadsIntoAnotherNumberOfIngredients() {
        Outcome outcome = reread(recipe(7, "lechuga (80 gr) + tomate (100 gr)",
                unmatched(1, "lechuga (80 gr) + tomate (100 gr)", "1", "unidad")));

        assertThat(outcome.changed()).isEmpty();
        assertThat(outcome.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.id()).isEqualTo(7);
            assertThat(skipped.stored()).isEqualTo(1);
            assertThat(skipped.read()).isEqualTo(2);
        });
        assertThat(outcome.reread()).isZero();
    }

    /** Pairs by position: the n-th fragment is the n-th row, so a name written twice still pairs right. */
    @Test
    void pairsByPosition() {
        Outcome outcome = reread(recipe(1, "1 kiwi + 2 kiwi mediano",
                stored(1, "1 kiwi", "1", "unidad", OTHER_FOOD, null, false),
                unmatched(2, "2 kiwi mediano", "1", "unidad")));

        assertThat(outcome.changed()).extracting(RereadIngredient::id, RereadIngredient::compositionFoodId,
                RereadIngredient::size)
                .containsExactly(
                        Tuple.tuple(1L, OTHER_FOOD, null),
                        Tuple.tuple(2L, KIWI, PortionSize.MEDIUM));
        assertThat(outcome.changed().get(1).quantity()).isEqualByComparingTo("2");
    }

    @Test
    void writesNothingForARowTheParserAlreadyReadTheSameWay() {
        Outcome outcome = reread(recipe(1, "lechuga (80 gr)",
                stored(1, "lechuga", "80.00", "gr", null, null, false)));

        assertThat(outcome.changed()).isEmpty();
        assertThat(outcome.reread()).isEqualTo(1);
    }

    @Test
    void leavesABrandedMatchAndItsMeasureAsTheyWere() {
        StoredIngredient branded = new StoredIngredient(1, "1 cdta AOVE", BigDecimal.ONE, null, "unidad",
                null, null, 500L, null, OLD_RULE_MEASURE, false);
        Outcome outcome = reread(new StoredRecipe(1, "1 cdta AOVE", List.of(branded)));

        RereadIngredient oil = outcome.changed().get(0);
        assertThat(oil.rawName()).isEqualTo("AOVE");
        assertThat(oil.compositionFoodId()).isNull();
        assertThat(oil.foodMeasureId()).isEqualTo(OLD_RULE_MEASURE);
    }

    private Outcome reread(StoredRecipe... recipes) {
        return RecipeReread.reread(List.of(recipes), parser, this::lookup, rule);
    }

    private static StoredRecipe recipe(long id, String text, StoredIngredient... ingredients) {
        return new StoredRecipe(id, text, List.of(ingredients));
    }

    private static StoredIngredient unmatched(long id, String name, String quantity, String unit) {
        return stored(id, name, quantity, unit, null, null, false);
    }

    private static StoredIngredient stored(long id, String name, String quantity, String unit, Long food,
                                           Long measure, boolean picked) {
        return new StoredIngredient(id, name, new BigDecimal(quantity), null, unit, null, null, null, food,
                measure, picked);
    }

    private Map<String, Long> lookup(Collection<String> names) {
        asked.add(List.copyOf(names));
        Map<String, Long> found = new HashMap<>();
        names.stream().filter(CROSSWALK::containsKey).forEach(name -> found.put(name, CROSSWALK.get(name)));
        return found;
    }
}
