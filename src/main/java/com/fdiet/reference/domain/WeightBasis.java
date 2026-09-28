package com.fdiet.reference.domain;

/**
 * Whether a published weight is of the part that is eaten or of the food as it
 * is bought.
 *
 * <p>Composition figures are per 100 g of edible portion, so a gross weight — an
 * orange with its peel, a chicken thigh with its bone — has to be reduced by the
 * food's edible fraction before it is priced, and is left unweighed when that
 * fraction is not known.
 */
public enum WeightBasis {
    NET_EDIBLE,
    GROSS,
    UNSPECIFIED
}
