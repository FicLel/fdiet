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
}
