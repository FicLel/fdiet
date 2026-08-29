package com.fdiet.diet.helpers;

import java.math.BigDecimal;

/**
 * Turns the quantity a diet prescribes into a multiple of the 100 g every
 * composition figure is published for.
 */
public interface IPortionScaler {

    /**
     * The factor to multiply a per-100 g figure by, or {@code null} when the
     * quantity cannot be weighed — "1 unidad", "1 cdta", "2 lonchas". Null is
     * the honest answer there: guessing what a spoonful weighs would put a
     * made-up number in someone's diet.
     */
    BigDecimal factorOf(BigDecimal quantity, String unit);
}
