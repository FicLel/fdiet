package com.fdiet.reference.domain;

import com.fdiet.common.helper.Texts;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The state a weight was written in, or published for.
 *
 * <p>It matters because the same food changes weight when it is cooked: 70 g of
 * raw rice is roughly 180 g boiled, and pricing a raw weight against boiled rice
 * is a threefold error. Nothing converts one into the other here. A state is
 * read, kept and compared, and a disagreement is shown rather than corrected.
 *
 * <p>{@link #UNSPECIFIED} is what most published rations and most BEDCA names
 * say — nothing — and it never counts as a disagreement.
 */
public enum FoodState {
    RAW,
    /** "En seco": pasta, rice and legumes weighed before they are cooked. */
    DRY,
    COOKED,
    CANNED,
    DRAINED,
    UNSPECIFIED;

    private static final List<Rule> NAME_RULES = List.of(
            // "Cocinado" is also the word the composer and the ration notes write for
            // COOKED, so the text they produce reads back in the state it was written in.
            new Rule(COOKED, "hervid[oa]s?", "cocid[oa]s?", "cocinad[oa]s?", "plancha", "asad[oa]s?", "frit[oa]s?",
                    "horno", "horneado", "estofad[oa]s?", "guisad[oa]s?", "escalfad[oa]s?",
                    "parrilla", "vapor", "salteado", "salteada", "brasa", "rebozad[oa]s?",
                    "empanad[oa]s?", "duro", "pasado por agua", "revuelto"),
            new Rule(CANNED, "en conserva", "conserva", "enlatad[oa]s?", "en lata", "de bote",
                    "en su jugo", "en almibar"),
            new Rule(DRAINED, "escurrid[oa]s?"),
            new Rule(DRY, "en seco", "sec[oa]s?", "desecad[oa]s?", "deshidratad[oa]s?"),
            new Rule(RAW, "crud[oa]s?", "en crudo"));

    /**
     * The words fdiet writes for this state after a weight ({@code 150 g cocinado}),
     * or null for {@link #UNSPECIFIED}, which writes nothing. One table for every
     * writer — the composer's fragment and the ration notes — and each word reads
     * back through {@link #ofWriting} as this state (FD-060).
     */
    public String written() {
        return switch (this) {
            case RAW -> "en crudo";
            case DRY -> "en seco";
            case COOKED -> "cocinado";
            case CANNED -> "en conserva";
            case DRAINED -> "escurrido";
            case UNSPECIFIED -> null;
        };
    }

    /** Raw and dry are both weighed before cooking; the others after it, or as sold. */
    public boolean uncooked() {
        return this == RAW || this == DRY;
    }

    public boolean cooked() {
        return this == COOKED || this == CANNED || this == DRAINED;
    }

    /**
     * Whether a weight in this state priced against a food published in the
     * other one is a different amount of food. Unknown on either side is not a
     * disagreement: a blank never raises a flag.
     */
    public static boolean disagree(FoodState written, FoodState published) {
        if (written == null || published == null) {
            return false;
        }
        return written.uncooked() && published.cooked() || written.cooked() && published.uncooked();
    }

    /**
     * The state a composition-database name states outright: {@code Lenteja,
     * hervida} is cooked, {@code Lenteja, seca, cruda} dry, {@code Lechuga}
     * says nothing.
     *
     * <p>Read from the name because the name is what the source wrote in words.
     * The LanguaL facets BEDCA also publishes were checked and are not
     * dependable for this — {@code Lengua, de ternera, cruda} is coded as baked
     * and {@code Vinagre de manzana} as boiled.
     */
    public static FoodState ofFoodName(String name) {
        String text = words(name);
        if (text.isEmpty()) {
            return UNSPECIFIED;
        }
        // "Seca, cruda" is dry; a name that says both cooked and canned is cooked.
        boolean dry = matches(DRY, text);
        boolean raw = matches(RAW, text);
        boolean cooked = matches(COOKED, text);
        boolean canned = matches(CANNED, text);
        if (cooked) {
            return COOKED;
        }
        if (canned) {
            return CANNED;
        }
        if (matches(DRAINED, text)) {
            return DRAINED;
        }
        if (dry) {
            return DRY;
        }
        return raw ? RAW : UNSPECIFIED;
    }

    /**
     * The state a written fragment states, or null when it states none or
     * contradicts itself. {@code arroz blanco (70 g crudo)} is raw;
     * {@code lentejas (60 g en crudo, 180 g cocidas)} names both and is left
     * unknown rather than guessed at.
     *
     * <p>When the name and the bracket each state one side of cooking and the two
     * disagree — {@code Lenteja, cocida (55 g en seco)}, {@code pechuga a la plancha
     * (150 g en crudo)} — <strong>the bracket wins</strong>: it says in which state
     * the grams were weighed, while the name still says which food it is (FD-052).
     * Read as one, the two used to cancel out into null, and the weight was then
     * priced against whatever the food was published as. Anything else — one side
     * silent, both on the same side, a bracket contradicting itself — reads as
     * before.
     *
     * <p>"Seco" alone is only read inside brackets: outside them it is far more
     * often part of the food — {@code frutos secos}.
     */
    public static FoodState ofWriting(String outside, String inside) {
        String out = words(outside);
        String in = words(inside);
        FoodState named = read(out, "");
        FoodState weighed = read("", in);
        if (disagree(weighed, named)) {
            return weighed;
        }
        return read(out, in);
    }

    /** The state that the words outside and inside a bracket state together, or null. */
    private static FoodState read(String out, String in) {
        String both = (out + " " + in).trim();
        boolean uncooked = matches(RAW, both) || both.matches(".*\\ben seco\\b.*")
                || matches(DRY, in);
        boolean cooked = matches(COOKED, both);
        boolean canned = matches(CANNED, both);
        boolean drained = matches(DRAINED, both);
        int said = (uncooked ? 1 : 0) + (cooked || canned || drained ? 1 : 0);
        if (said != 1) {
            return null;
        }
        if (uncooked) {
            boolean dry = both.matches(".*\\ben seco\\b.*") || matches(DRY, in);
            return dry ? DRY : RAW;
        }
        if (drained) {
            return DRAINED;
        }
        return canned && !cooked ? CANNED : COOKED;
    }

    private static boolean matches(FoodState state, String text) {
        return NAME_RULES.stream()
                .filter(rule -> rule.state() == state)
                .anyMatch(rule -> rule.pattern().matcher(text).find());
    }

    /** Lower case, no accents, punctuation turned into spaces. */
    private static String words(String text) {
        String key = Texts.key(text);
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ñ]+", " ").trim();
    }

    private record Rule(FoodState state, Pattern pattern) {
        Rule(FoodState state, String... words) {
            this(state, Pattern.compile("\\b(" + String.join("|", Arrays.asList(words)) + ")\\b"));
        }
    }
}
