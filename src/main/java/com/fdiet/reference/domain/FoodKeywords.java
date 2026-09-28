package com.fdiet.reference.domain;

import com.fdiet.common.helper.Texts;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Which foods a reference row covers, read off their names.
 *
 * <p>A row lists phrases separated by semicolons ({@code queso fresco;requeson}),
 * written without accents. A phrase covers a name when its words appear in it
 * one after another, each word allowed its Spanish plural ({@code macarron}
 * covers {@code Macarrones}). The longer the phrase, the more specific the
 * match, which is how {@code queso fresco} is preferred to {@code queso} for
 * {@code Queso fresco de burgos}.
 *
 * <p>A phrase starting with {@code !} excludes: {@code yogur;!liquido} covers
 * every yogurt but the drinkable ones, which are not sold in the unit the row
 * weighs.
 */
public final class FoodKeywords {

    /** How much longer than the keyword word a name word may be: "-s", "-es". */
    private static final int PLURAL_SLACK = 2;

    private static final String EXCLUDE = "!";

    private FoodKeywords() {
    }

    /** The including phrases of a keywords column, empty when it has none. */
    public static List<String> phrases(String keywords) {
        return split(keywords, false);
    }

    /**
     * How specific the best phrase covering the name is — its length in
     * characters — or -1 when none covers it, or an excluding phrase does. A
     * row with no including phrases covers every name of its family and scores
     * 0, below any phrase that matches.
     */
    public static int specificity(String keywords, String foodName) {
        String[] name = words(foodName).split(" ");
        for (String excluded : split(keywords, true)) {
            if (covers(name, excluded.split(" "))) {
                return -1;
            }
        }
        List<String> phrases = phrases(keywords);
        if (phrases.isEmpty()) {
            return 0;
        }
        int best = -1;
        for (String phrase : phrases) {
            if (covers(name, phrase.split(" "))) {
                best = Math.max(best, phrase.length());
            }
        }
        return best;
    }

    private static List<String> split(String keywords, boolean excluding) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split(";"))
                .map(String::trim)
                .filter(phrase -> phrase.startsWith(EXCLUDE) == excluding)
                .map(phrase -> words(excluding ? phrase.substring(EXCLUDE.length()) : phrase))
                .filter(phrase -> !phrase.isEmpty())
                .toList();
    }

    private static boolean covers(String[] name, String[] phrase) {
        for (int start = 0; start + phrase.length <= name.length; start++) {
            boolean all = true;
            for (int at = 0; at < phrase.length && all; at++) {
                all = sameWord(name[start + at], phrase[at]);
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameWord(String written, String keyword) {
        return written.startsWith(keyword) && written.length() - keyword.length() <= PLURAL_SLACK
                && (written.length() == keyword.length() || written.endsWith("s"));
    }

    private static String words(String text) {
        String key = Texts.key(text);
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
