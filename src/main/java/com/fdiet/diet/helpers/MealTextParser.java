package com.fdiet.diet.helpers;

import com.fdiet.common.helper.Numbers;
import com.fdiet.common.helper.Texts;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the shape a diet is actually written in:
 *
 * <pre>
 * Ensalada: lechuga (80 gr) + tomate (100 gr) + 1 cdta AOVE
 * ^ dish     ^ ingredient    ^ ingredient      ^ a count and a household measure
 * </pre>
 *
 * <p>A colon names the dish and a {@code +} separates its ingredients, but only
 * outside brackets — {@code (20 g: nueces + almendras)} is one ingredient, not
 * three — and the colon only names the dish when it comes before the first
 * separator. Otherwise {@code "Atún (160 gr) + judías al vapor: judías (200 gr)"}
 * would lose the tuna, and it is the second rule that keeps it.
 *
 * <p><strong>Two ways of writing a quantity.</strong> In brackets after the
 * food, {@code lechuga (80 gr)} or {@code aceite (1 cucharada sopera)}, where the
 * last quantity wins; or in front of it with no brackets at all,
 * {@code 1 cdta AOVE}, {@code 2 lonchas de pavo}, {@code 1 kiwi}. The second is
 * only read when the fragment has no brackets: a bracketed fragment is read
 * exactly as it always was, name included, so a week imported before still
 * stores the same ingredients.
 *
 * <p>The words a fragment carries about the food are kept beside it rather than
 * thrown away: the raw/cooked word ({@code arroz blanco (70 g crudo)}) and the
 * size of a piece ({@code 1 pera pequeña}). They stay in the name as well —
 * the name is what the nutritionist wrote.
 *
 * <p>Nothing is ever dropped. A fragment that carries no readable quantity
 * still becomes an ingredient, of one {@code unidad}, holding the text as
 * written; the nutritionist corrects it afterwards.
 */
@Component
public class MealTextParser implements IMealTextParser {

    /** The width of {@code diet_ingredients.raw_name} and of the dish/meal name columns. */
    private static final int NAME_MAX = 255;

    /** The width of {@code diet_dishes.raw_text}. */
    private static final int TEXT_MAX = 1000;

    /** The width of {@code diet_ingredients.unit}. */
    private static final int UNIT_MAX = 32;

    private static final String DEFAULT_UNIT = "unidad";
    private static final BigDecimal DEFAULT_QUANTITY = BigDecimal.ONE;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    /** {@code 80}, {@code 12,5}, {@code 1/2} — the number a quantity starts with. */
    private static final Pattern NUMBER = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(?:/\\s*(\\d+(?:[.,]\\d+)?))?");

    /** The unit word straight after a number: {@code 80gr}, {@code 250 mL}. */
    private static final Pattern UNIT_WORD = Pattern.compile("^\\s*(\\p{L}+)");

    /** A count at the start of a fragment written without brackets. */
    private static final Pattern LEADING_COUNT = Pattern.compile(
            "^\\s*(\\d+(?:[.,]\\d+)?(?:\\s*/\\s*\\d+(?:[.,]\\d+)?)?|un|una|uno|medio|media)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    /** The "de" between a measure and its food: {@code 2 lonchas de pavo}. */
    private static final Pattern LINKING_DE = Pattern.compile("^(?i)(de|del)\\s+");

    private static final char OPEN = '(';
    private static final char CLOSE = ')';
    private static final char SEPARATOR = '+';
    private static final char COLON = ':';

    @Override
    public Dish parse(String text, String fallbackName) {
        String cell = Texts.trimToNull(text);
        if (cell == null) {
            return null;
        }

        int separator = indexAtTopLevel(cell, SEPARATOR, 0);
        int colon = indexAtTopLevel(cell, COLON, 0);
        boolean colonNamesTheDish = colon > 0 && (separator < 0 || colon < separator);

        String name = colonNamesTheDish ? cell.substring(0, colon) : fallbackName;
        String body = colonNamesTheDish ? cell.substring(colon + 1) : cell;

        List<DishIngredient> ingredients = new ArrayList<>();
        for (String fragment : splitAtTopLevel(body, SEPARATOR)) {
            DishIngredient ingredient = toIngredient(fragment);
            if (ingredient != null) {
                ingredients.add(ingredient);
            }
        }
        if (ingredients.isEmpty()) {
            // The body held no readable fragment; the cell itself is the ingredient.
            ingredients.add(new DishIngredient(clean(cell), DEFAULT_QUANTITY, DEFAULT_UNIT));
        }
        // The cell travels beside what was read out of it: reading is lossy, and
        // whoever wrote the sentence is entitled to get that sentence back.
        return new Dish(clean(name == null ? cell : name),
                Texts.truncate(cell, TEXT_MAX),
                List.copyOf(ingredients));
    }

    /** One fragment between two {@code +}. Null only when it is blank. */
    private DishIngredient toIngredient(String fragment) {
        String trimmed = Texts.trimToNull(fragment);
        if (trimmed == null) {
            return null;
        }

        int open = trimmed.indexOf(OPEN);
        if (open < 0) {
            return withoutBrackets(nameOf(trimmed));
        }
        // The first bracket, not the last: it is the one that belongs to the
        // food named just before it, and a fragment can carry more than one.
        int close = closeOf(trimmed, open);
        String inside = close < 0 ? trimmed.substring(open + 1) : trimmed.substring(open + 1, close);
        String after = close < 0 || close + 1 >= trimmed.length() ? "" : trimmed.substring(close + 1);

        String outside = trimmed.substring(0, open) + " " + after;
        String name = nameOf(outside);
        if (Texts.trimToNull(name) == null) {
            // "(2 rebanadas)" and nothing else: the brackets are all there is.
            name = trimmed;
        }

        Quantity quantity = readQuantity(inside);
        return new DishIngredient(clean(name), quantity.amount(), quantity.unit(),
                FoodState.ofWriting(outside, inside), PortionSize.ofWriting(outside + " " + inside));
    }

    /**
     * A fragment with no brackets: {@code 1 cdta AOVE}, {@code 2 lonchas de pavo},
     * {@code 1 kiwi}, {@code sal}. A leading count followed by a household
     * measure or a weight reads as that quantity; a leading count followed by
     * the food alone is that many pieces; anything else is one unit of the whole
     * text, as it always was.
     */
    private DishIngredient withoutBrackets(String text) {
        FoodState state = FoodState.ofWriting(text, "");
        PortionSize size = PortionSize.ofWriting(text);
        Matcher leading = LEADING_COUNT.matcher(fractions(text));
        if (!leading.matches()) {
            return new DishIngredient(clean(text), DEFAULT_QUANTITY, DEFAULT_UNIT, state, size);
        }
        BigDecimal amount = countOf(leading.group(1));
        String rest = leading.group(2).trim();
        if (amount == null || amount.signum() <= 0) {
            return new DishIngredient(clean(text), DEFAULT_QUANTITY, DEFAULT_UNIT, state, size);
        }

        String unit = DEFAULT_UNIT;
        String food = rest;
        Optional<HouseholdMeasure.Reading> measure = HouseholdMeasure.readAt(rest);
        if (measure.isPresent()) {
            unit = measure.get().written();
            food = rest.substring(measure.get().written().length());
        } else {
            Matcher word = UNIT_WORD.matcher(rest);
            if (word.find() && PortionScaler.isWeightOrVolume(word.group(1))) {
                unit = word.group(1);
                food = rest.substring(word.end());
            }
        }
        food = LINKING_DE.matcher(food.replaceFirst("^[.\\s]+", "")).replaceFirst("").trim();
        if (food.isEmpty()) {
            // "1 unidad" with nothing after it: the text is all there is to call it.
            return new DishIngredient(clean(text), amount, Texts.truncate(unit, UNIT_MAX), state, size);
        }
        return new DishIngredient(clean(food), amount, Texts.truncate(unit, UNIT_MAX), state, size);
    }

    /**
     * A fragment can carry a colon of its own — {@code "judías al vapor: judías
     * verdes (200 gr)"} — and what follows it is the food.
     */
    private String nameOf(String fragment) {
        int colon = indexAtTopLevel(fragment, COLON, 0);
        String name = colon < 0 ? fragment : fragment.substring(colon + 1);
        return name.replaceAll("\\s+", " ").trim();
    }

    /**
     * The last number and unit in the brackets. {@code (1/2 unidad, 80 gr)}
     * gives {@code 80 gr}: the weight is what a diet is read by, and it is
     * written last. A unit that is a household measure of more than one word is
     * read whole — {@code (1 cucharada sopera)} is a cucharada sopera, not a
     * cucharada.
     */
    private Quantity readQuantity(String inside) {
        String text = fractions(inside);
        Matcher matcher = NUMBER.matcher(text);
        BigDecimal amount = null;
        String unit = null;
        while (matcher.find()) {
            BigDecimal numerator = Numbers.toDecimal(matcher.group(1));
            if (numerator == null) {
                continue;
            }
            BigDecimal denominator = Numbers.toDecimal(matcher.group(2));
            amount = denominator == null || denominator.signum() == 0
                    ? numerator
                    : numerator.divide(denominator, 2, RoundingMode.HALF_UP);
            unit = unitAfter(text.substring(matcher.end()));
        }
        if (amount == null || amount.signum() <= 0) {
            return new Quantity(DEFAULT_QUANTITY, DEFAULT_UNIT);
        }
        return new Quantity(amount, Texts.truncate(unit == null ? DEFAULT_UNIT : unit, UNIT_MAX));
    }

    private static String unitAfter(String rest) {
        String stripped = rest.stripLeading();
        Optional<HouseholdMeasure.Reading> measure = HouseholdMeasure.readAt(stripped);
        if (measure.isPresent()) {
            return measure.get().written();
        }
        Matcher word = UNIT_WORD.matcher(rest);
        return word.find() ? word.group(1) : null;
    }

    /** {@code 2}, {@code 1/2}, {@code una}, {@code media}. */
    private static BigDecimal countOf(String written) {
        String word = written.toLowerCase(Locale.ROOT);
        if (word.equals("un") || word.equals("una") || word.equals("uno")) {
            return BigDecimal.ONE;
        }
        if (word.equals("medio") || word.equals("media")) {
            return HALF;
        }
        Matcher number = NUMBER.matcher(written);
        if (!number.matches()) {
            return null;
        }
        BigDecimal numerator = Numbers.toDecimal(number.group(1));
        BigDecimal denominator = Numbers.toDecimal(number.group(2));
        if (numerator == null) {
            return null;
        }
        return denominator == null || denominator.signum() == 0
                ? numerator
                : numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /** Rewrites the typographic fractions so the quantity pattern can read them. */
    private static String fractions(String text) {
        return text.replace("½", "1/2").replace("¼", "1/4").replace("¾", "3/4").replace("⅓", "1/3");
    }

    private static String clean(String value) {
        String collapsed = value.replaceAll("\\s+", " ").trim();
        // A name is never blank: a blank one would fail validation and lose the row.
        return Texts.truncate(collapsed.isEmpty() ? "-" : collapsed, NAME_MAX);
    }

    /** The bracket closing the one at {@code open}, or -1 when it is never closed. */
    private static int closeOf(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == OPEN) {
                depth++;
            } else if (c == CLOSE && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The first {@code needle} that is not inside brackets, or -1. */
    private static int indexAtTopLevel(String text, char needle, int from) {
        int depth = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == OPEN) {
                depth++;
            } else if (c == CLOSE) {
                depth = Math.max(0, depth - 1);
            } else if (c == needle && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitAtTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int at = indexAtTopLevel(text, separator, 0); at >= 0;
             at = indexAtTopLevel(text, separator, start)) {
            parts.add(text.substring(start, at));
            start = at + 1;
        }
        parts.add(text.substring(start));
        return parts;
    }

    private record Quantity(BigDecimal amount, String unit) {
    }
}
