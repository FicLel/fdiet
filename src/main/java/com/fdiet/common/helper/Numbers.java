package com.fdiet.common.helper;

import java.math.BigDecimal;

/**
 * Reads the numbers of the Spanish source data, where the decimal separator is
 * a comma as often as a dot.
 */
public final class Numbers {

    private Numbers() {
    }

    /** Accepts both {@code 12.5} and the comma-decimal {@code 12,5}; anything else is null. */
    public static BigDecimal toDecimal(String value) {
        String trimmed = Texts.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return new BigDecimal(trimmed.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The decimal as a long, or null when it does not read as a number. */
    public static Long toLong(String value) {
        BigDecimal decimal = toDecimal(value);
        return decimal == null ? null : decimal.longValue();
    }

    /** The decimal as an int, or null when it does not read as a number. */
    public static Integer toInteger(String value) {
        BigDecimal decimal = toDecimal(value);
        return decimal == null ? null : decimal.intValue();
    }
}
