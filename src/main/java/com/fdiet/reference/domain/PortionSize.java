package com.fdiet.reference.domain;

import com.fdiet.common.helper.Texts;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "Pequeña", "mediana", "grande" — which row of a household measure can weigh
 * {@code 1 pera pequeña}. A piece written without a size may be weighed by a
 * row of any size only when there is a single one to choose.
 */
public enum PortionSize {
    SMALL,
    MEDIUM,
    LARGE;

    /**
     * The size a piece of writing names, or null when it names none or more
     * than one: {@code 2 Uds. pequeñas o 1 Ud. grande} is not one size.
     */
    public static PortionSize ofWriting(String text) {
        String key = Texts.key(text);
        if (key == null) {
            return null;
        }
        String words = " " + key.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ") + " ";
        boolean small = words.matches(".* (pequen[oa]s?|pequenit[oa]s?|chic[oa]s?) .*");
        boolean medium = words.matches(".* (median[oa]s?) .*");
        boolean large = words.matches(".* (grandes?) .*");
        int named = (small ? 1 : 0) + (medium ? 1 : 0) + (large ? 1 : 0);
        if (named != 1) {
            return null;
        }
        return small ? SMALL : medium ? MEDIUM : LARGE;
    }

    private static final Pattern SIZE_WORD = Pattern.compile(
            "(?iu)(?<![\\p{L}])(peque[ñn](it)?[oa]s?|chic[oa]s?|median[oa]s?|grandes?)(?![\\p{L}])");

    /**
     * The writing without its size words, or null when it has none:
     * {@code kiwi mediano} is {@code kiwi}. A size says how big the piece is,
     * never which food it is, so a name may be looked up without one.
     */
    public static String withoutSize(String text) {
        if (text == null || !SIZE_WORD.matcher(text).find()) {
            return null;
        }
        String stripped = SIZE_WORD.matcher(text).replaceAll(" ").replaceAll("\\s+", " ").strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
