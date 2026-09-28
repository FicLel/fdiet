package com.fdiet.reference.domain;

import com.fdiet.common.helper.Texts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The kitchen words a quantity is said in, and the spellings a diet writes them.
 *
 * <p>This is vocabulary, not weight. What a {@code cucharada sopera} of oil
 * weighs depends on the food and on who published it, and lives in
 * {@code ref_food_measures}; this only says that {@code cda}, {@code cs} and
 * {@code cucharada sopera} are the same word, so the parser can read one and
 * the reference table can weigh it. Kept in code for the reason
 * {@code FoodCategoriser} keeps its word list in code: the parser needs it
 * before anything is loaded, and a word claimed by two measures is a startup
 * failure rather than a silent tie.
 *
 * <p>Plain {@code cucharada} and {@code cda} read as {@code cucharada sopera},
 * which is what they mean in a Spanish diet ({@code cucharadita}/{@code cdta}
 * is the small one). Whatever weighs one still shows the row it came from, so
 * the reading is visible wherever it is used.
 */
public enum HouseholdMeasure {
    UNIDAD("unidad", "unidades", true,
            "unidad", "unidades", "ud", "uds", "u", "pieza", "piezas"),
    CUCHARADA_SOPERA("cucharada sopera", "cucharadas soperas", true,
            "cucharada sopera", "cucharadas soperas", "cuchara sopera", "cucharadas", "cucharada",
            "cda", "cdas", "cs"),
    CUCHARADA_POSTRE("cucharada de postre", "cucharadas de postre", true,
            "cucharada de postre", "cucharadas de postre", "cucharada postre", "cucharadas postre"),
    CUCHARADITA("cucharadita", "cucharaditas", true,
            "cucharadita", "cucharaditas", "cdta", "cdtas", "cdita", "cditas",
            "cucharada de cafe", "cucharadas de cafe", "cucharilla", "cucharillas"),
    VASO("vaso", "vasos", false, "vaso", "vasos"),
    TAZA("taza", "tazas", true, "taza", "tazas"),
    TAZON("tazón", "tazones", false, "tazon", "tazones"),
    PLATO("plato", "platos", false, "plato", "platos"),
    CAZO("cazo", "cazos", false, "cazo", "cazos"),
    LONCHA("loncha", "lonchas", true, "loncha", "lonchas", "lonchita", "lonchitas"),
    REBANADA("rebanada", "rebanadas", true, "rebanada", "rebanadas"),
    RODAJA("rodaja", "rodajas", true, "rodaja", "rodajas"),
    FILETE("filete", "filetes", false, "filete", "filetes"),
    PUNADO("puñado", "puñados", false, "punado", "punados"),
    DIENTE("diente", "dientes", false, "diente", "dientes"),
    PORCION("porción", "porciones", true, "porcion", "porciones"),
    LATA("lata", "latas", true, "lata", "latas"),
    RACION("ración", "raciones", true, "racion", "raciones");

    private final String label;
    private final String plural;
    private final boolean feminine;
    private final List<String> aliases;

    HouseholdMeasure(String label, String plural, boolean feminine, String... aliases) {
        this.label = label;
        this.plural = plural;
        this.feminine = feminine;
        this.aliases = List.of(aliases);
    }

    /** Every spelling, longest first, so "cucharada sopera" wins over "cucharada". */
    private static final List<Map.Entry<String, HouseholdMeasure>> BY_ALIAS = aliasesLongestFirst();

    public String label() {
        return label;
    }

    /**
     * How a count of this measure is written back into a cell, the size agreeing
     * with it: {@code 1 unidad mediana}, {@code 2 platos pequeños}. Always a
     * spelling the parser reads.
     */
    public String written(boolean several, PortionSize size) {
        String word = several ? plural : label;
        if (size == null) {
            return word;
        }
        String adjective = switch (size) {
            case SMALL -> "pequeñ";
            case MEDIUM -> "median";
            case LARGE -> "grande";
        };
        if (size != PortionSize.LARGE) {
            adjective += feminine ? "a" : "o";
        }
        return word + " " + adjective + (several ? "s" : "");
    }

    public List<String> aliases() {
        return aliases;
    }

    /**
     * The measure a unit names, or empty for a weight, a volume or a word this
     * list does not know. Compared without accents or case: {@code Cucharadas},
     * {@code cucharadas} and {@code CUCHARADAS} are one word.
     */
    public static Optional<HouseholdMeasure> ofUnit(String unit) {
        String wanted = normalised(unit);
        if (wanted == null) {
            return Optional.empty();
        }
        return BY_ALIAS.stream()
                .filter(entry -> entry.getKey().equals(wanted))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * The longest spelling a text starts with, and how many characters of the
     * text it took, or empty when it starts with none. Words only end at a
     * word boundary: {@code cdta} is not read out of {@code cdtas} half-way.
     */
    public static Optional<Reading> readAt(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String lowered = foldPreservingLength(text);
        for (Map.Entry<String, HouseholdMeasure> entry : BY_ALIAS) {
            String alias = entry.getKey();
            if (lowered.startsWith(alias)
                    && (lowered.length() == alias.length()
                    || !Character.isLetter(lowered.charAt(alias.length())))) {
                return Optional.of(new Reading(entry.getValue(), text.substring(0, alias.length())));
            }
        }
        return Optional.empty();
    }

    /** What was read and the text it was read from, as written. */
    public record Reading(HouseholdMeasure measure, String written) {
    }

    private static String normalised(String unit) {
        String key = Texts.key(unit);
        return key == null ? null : key.toLowerCase(Locale.ROOT).replaceAll("\\.", "").trim();
    }

    /**
     * Lower case and without accents, one character for one character, so a
     * match can be cut back out of the original text at the same length.
     */
    private static String foldPreservingLength(String text) {
        StringBuilder folded = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            String single = Texts.key(String.valueOf(c));
            folded.append(single == null || single.length() != 1
                    ? Character.toLowerCase(c)
                    : Character.toLowerCase(single.charAt(0)));
        }
        return folded.toString();
    }

    private static List<Map.Entry<String, HouseholdMeasure>> aliasesLongestFirst() {
        Map<String, HouseholdMeasure> byAlias = new LinkedHashMap<>();
        for (HouseholdMeasure measure : values()) {
            for (String alias : measure.aliases) {
                HouseholdMeasure taken = byAlias.putIfAbsent(alias, measure);
                if (taken != null && taken != measure) {
                    throw new IllegalStateException(
                            "\"" + alias + "\" is claimed by both " + taken + " and " + measure);
                }
            }
        }
        List<Map.Entry<String, HouseholdMeasure>> entries = new ArrayList<>(byAlias.entrySet());
        entries.sort(Comparator.comparingInt(
                (Map.Entry<String, HouseholdMeasure> entry) -> entry.getKey().length()).reversed());
        return List.copyOf(entries);
    }
}
