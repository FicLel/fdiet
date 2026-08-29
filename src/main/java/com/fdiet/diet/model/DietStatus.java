package com.fdiet.diet.model;

/**
 * Where a stored diet stands. Exactly one diet is {@link #ACTIVE} at a time —
 * the {@code uk_diets_active} unique index makes that a database rule, not a
 * convention — and every diet that came before it is {@link #ARCHIVED}.
 */
public enum DietStatus {
    ACTIVE,
    ARCHIVED
}
