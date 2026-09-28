package com.fdiet.diet.helpers;

import com.fdiet.common.helper.Texts;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * The unit table of a diet, and the one assumption in it.
 *
 * <p>Millilitres are taken as grams. A diet's liquids are water, broth, milk
 * and juice, all within a few percent of 1 g/ml, and refusing to scale them
 * would leave a third of a postoperative week uncounted. Everything that is not
 * a weight or a volume — a unit, a spoonful, a slice — is left uncounted
 * instead of guessed, and {@link com.fdiet.diet.dto.NutritionSummaryDto} says
 * how many those were.
 */
@Component
public class PortionScaler implements IPortionScaler {

    /** Composition figures are published per this many grams. */
    private static final BigDecimal PER = new BigDecimal("100");

    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    /** Enough places that a milligram does not round to nothing. */
    private static final int SCALE = 8;

    /** Grams per unit of measure. Anything absent cannot be weighed. */
    private static final Map<String, BigDecimal> GRAMS_PER_UNIT = Map.ofEntries(
            Map.entry("G", BigDecimal.ONE),
            Map.entry("GR", BigDecimal.ONE),
            Map.entry("GRS", BigDecimal.ONE),
            Map.entry("GRAMO", BigDecimal.ONE),
            Map.entry("GRAMOS", BigDecimal.ONE),
            Map.entry("KG", THOUSAND),
            Map.entry("KILO", THOUSAND),
            Map.entry("KILOS", THOUSAND),
            Map.entry("KILOGRAMO", THOUSAND),
            Map.entry("KILOGRAMOS", THOUSAND),
            Map.entry("MG", BigDecimal.ONE.divide(THOUSAND, SCALE, RoundingMode.HALF_UP)),
            // Taken as 1 g/ml; see the class note.
            Map.entry("ML", BigDecimal.ONE),
            Map.entry("CC", BigDecimal.ONE),
            Map.entry("MILILITRO", BigDecimal.ONE),
            Map.entry("MILILITROS", BigDecimal.ONE),
            Map.entry("L", THOUSAND),
            Map.entry("LITRO", THOUSAND),
            Map.entry("LITROS", THOUSAND));

    @Override
    public BigDecimal factorOf(BigDecimal quantity, String unit) {
        if (quantity == null || quantity.signum() <= 0) {
            return null;
        }
        BigDecimal gramsPerUnit = GRAMS_PER_UNIT.get(Texts.key(unit));
        if (gramsPerUnit == null) {
            return null;
        }
        return quantity.multiply(gramsPerUnit).divide(PER, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * A household measure is only used for a unit this table cannot weigh: "80 g"
     * is 80 g whatever measure was once attached to the ingredient. The measure
     * weighs one unit of it; a gross weight is cut to the edible part by the
     * food's published edible fraction, and left unweighed when that fraction is
     * unknown — composition figures are per 100 g of what is eaten.
     */
    @Override
    public Weighed weigh(BigDecimal quantity, String unit, MeasureWeight measure) {
        BigDecimal direct = factorOf(quantity, unit);
        if (direct != null) {
            return new Weighed(direct, false);
        }
        if (quantity == null || quantity.signum() <= 0 || measure == null
                || measure.gramsPerMeasure() == null) {
            return null;
        }
        BigDecimal grams = quantity.multiply(measure.gramsPerMeasure());
        if (measure.gross()) {
            BigDecimal edible = measure.ediblePortion();
            if (edible == null || edible.signum() <= 0 || edible.compareTo(BigDecimal.ONE) > 0) {
                return null;
            }
            grams = grams.multiply(edible);
        }
        return new Weighed(grams.divide(PER, SCALE, RoundingMode.HALF_UP), true);
    }

    @Override
    public boolean weighsDirectly(String unit) {
        return isWeightOrVolume(unit);
    }

    /** Whether the unit is one of the weights or volumes this table knows. */
    public static boolean isWeightOrVolume(String unit) {
        String key = Texts.key(unit);
        return key != null && GRAMS_PER_UNIT.containsKey(key);
    }
}
