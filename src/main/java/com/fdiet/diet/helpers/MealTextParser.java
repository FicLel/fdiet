package com.fdiet.diet.helpers;

import com.fdiet.common.helper.Numbers;
import com.fdiet.common.helper.Texts;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the shape a diet is actually written in:
 *
 * <pre>
 * Ensalada: lechuga (80 gr) + tomate (100 gr) + 1 cdta AOVE
 * ^ dish     ^ ingredient    ^ ingredient      ^ no quantity given
 * </pre>
 *
 * <p>A colon names the dish and a {@code +} separates its ingredients, but only
 * outside brackets — {@code (20 g: nueces + almendras)} is one ingredient, not
 * three — and the colon only names the dish when it comes before the first
 * separator. Otherwise {@code "Atún (160 gr) + judías al vapor: judías (200 gr)"}
 * would lose the tuna, and it is the second rule that keeps it.
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

    private static final String DEFAULT_UNIT = "unidad";
    private static final BigDecimal DEFAULT_QUANTITY = BigDecimal.ONE;

    /** {@code 80 gr}, {@code 250 mL}, {@code 1/2 unidad}, {@code 12,5 g}. */
    private static final Pattern QUANTITY = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(?:/\\s*(\\d+(?:[.,]\\d+)?))?\\s*(\\p{L}+)?");

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
            return new DishIngredient(clean(nameOf(trimmed)), DEFAULT_QUANTITY, DEFAULT_UNIT);
        }
        // The first bracket, not the last: it is the one that belongs to the
        // food named just before it, and a fragment can carry more than one.
        int close = closeOf(trimmed, open);
        String inside = close < 0 ? trimmed.substring(open + 1) : trimmed.substring(open + 1, close);
        String after = close < 0 || close + 1 >= trimmed.length() ? "" : trimmed.substring(close + 1);

        String name = nameOf(trimmed.substring(0, open) + " " + after);
        if (Texts.trimToNull(name) == null) {
            // "(2 rebanadas)" and nothing else: the brackets are all there is.
            name = trimmed;
        }

        Quantity quantity = readQuantity(inside);
        return new DishIngredient(clean(name), quantity.amount(), quantity.unit());
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
     * written last.
     */
    private Quantity readQuantity(String inside) {
        Matcher matcher = QUANTITY.matcher(fractions(inside));
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
            unit = Texts.trimToNull(matcher.group(3));
        }
        if (amount == null || amount.signum() <= 0) {
            return new Quantity(DEFAULT_QUANTITY, DEFAULT_UNIT);
        }
        return new Quantity(amount, Texts.truncate(unit == null ? DEFAULT_UNIT : unit, 32));
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
