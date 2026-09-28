package com.fdiet.reference.domain;

/**
 * How freely a source's figures may be used — a research classification, not
 * legal advice. Only A and B sources are loaded as figures; the others are
 * cited.
 */
public enum LicenceClass {
    /** Clearly reusable. */
    A,
    /** Reusable with attribution and whatever conditions the source names. */
    B,
    /** Useful, but permission is needed before the figures are loaded. */
    C,
    /** Reference only: consulted and cited, not loaded. */
    D,
    /** Licence unknown. */
    E
}
