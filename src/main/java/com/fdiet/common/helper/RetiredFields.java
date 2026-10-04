package com.fdiet.common.helper;

/**
 * Request fields that are still read only so they can be refused.
 *
 * <p>Jackson ignores a field a request type does not declare, so simply removing
 * {@code bedcaFoodId} would let an old caller's match vanish without a word. Each
 * request that used to take it keeps it, annotated {@code @Null} with this
 * message, and a value there is a 400 that says what to send instead. FD-033 phase E
 * removed BEDCA itself (V22); the refusal stays (decision 24), so an old caller
 * still hears why its match went nowhere.
 */
public final class RetiredFields {

    /** The message of a refused {@code bedcaFoodId}. */
    public static final String BEDCA_FOOD_ID = "is retired (FD-033): BEDCA foods are no longer "
            + "matched to; name a CIQUAL or BLS food by compositionFoodId";

    private RetiredFields() {
    }
}
