package com.fdiet.diet.helpers;

import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.model.ReferenceFoodMeasure;

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

    /**
     * The same factor, with one more way in: a household measure somebody
     * published a weight for ("1 cucharada sopera de aceite de oliva, 10 ml",
     * AESAN 2022). Null when neither the unit nor the measure can weigh it.
     */
    Weighed weigh(BigDecimal quantity, String unit, MeasureWeight measure);

    /**
     * The same, from the stored household-measure row: it weighs the quantity
     * only when the unit is the very measure the row is for — a row attached
     * for "cucharada" does not weigh a quantity since rewritten as "2 lonchas".
     * The week and the journal both weigh through here, so a spoon logged as an
     * extra weighs what the same spoon weighs inside the plan.
     *
     * @param ediblePortion the matched composition food's edible fraction, or null
     */
    default Weighed weigh(BigDecimal quantity, String unit, ReferenceFoodMeasure measure,
                          BigDecimal ediblePortion) {
        MeasureWeight weight = null;
        if (measure != null && HouseholdMeasure.ofUnit(unit)
                .filter(written -> written == measure.getMeasure()).isPresent()) {
            weight = new MeasureWeight(measure.gramsPerMeasure(),
                    measure.getWeightBasis() == WeightBasis.GROSS, ediblePortion);
        }
        return weigh(quantity, unit, weight);
    }

    /** Whether the unit is a weight or a volume, which needs no measure to be weighed. */
    boolean weighsDirectly(String unit);

    /**
     * What one household measure weighs, as far as the arithmetic needs to know.
     *
     * @param gramsPerMeasure one measure in grams; null for a published range
     * @param gross           whether that weight includes what is not eaten
     * @param ediblePortion   the matched food's published edible fraction
     */
    record MeasureWeight(BigDecimal gramsPerMeasure, boolean gross, BigDecimal ediblePortion) {
    }

    /** The factor, and whether a household measure was needed to reach it. */
    record Weighed(BigDecimal factor, boolean byMeasure) {
    }
}
