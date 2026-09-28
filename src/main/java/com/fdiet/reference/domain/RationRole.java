package com.fdiet.reference.domain;

/**
 * The place a food takes in a meal, which some guidelines size differently: the
 * AESAN/MEC school document gives 30 g of legumes for a main course and 15 g as
 * a side. A ration without a role is simply "one ration".
 */
public enum RationRole {
    PLATO_PRINCIPAL,
    GUARNICION,
    SOPA,
    POSTRE,
    ACOMPANAMIENTO,
    INGREDIENTE
}
