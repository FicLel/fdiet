package com.fdiet.common.helper;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The two things every importer does to a raw field before it becomes a column
 * value: drop it when it is blank, and cut it to the width of the column.
 */
public final class Texts {

    private Texts() {
    }

    /** The trimmed value, or {@code null} when it is null or blank. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The value cut to {@code maxLength} characters. Null passes through. */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** {@link #trimToNull} then {@link #truncate}. */
    public static String clean(String value, int maxLength) {
        return truncate(trimToNull(value), maxLength);
    }

    /**
     * A name reduced to the form two of them are compared in: lower case, its
     * runs of whitespace collapsed. Accents are left alone on purpose — the
     * columns these are matched against collate case- and accent-insensitively,
     * so stripping them here would only hide what is actually being compared.
     */
    public static String normaliseName(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * A label reduced to something a lookup table can be keyed on: upper case,
     * without accents, its runs of whitespace collapsed. {@code "Media mañana "}
     * and {@code "MEDIA MANANA"} both come back as {@code MEDIA MANANA}.
     */
    public static String key(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String withoutAccents = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutAccents.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
