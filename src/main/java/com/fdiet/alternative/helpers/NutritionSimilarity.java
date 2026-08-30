package com.fdiet.alternative.helpers;

import com.fdiet.food.dto.NutritionDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

/**
 * The distance arithmetic, and nothing else.
 *
 * <p>Each component is compared as a <em>relative</em> difference — how far
 * apart two figures are as a share of the larger — so a component measured in
 * hundreds of kilocalories and one measured in single grams of fibre count for
 * what they were weighted and not for the size of their numbers. The share is
 * taken against a floor as well as the larger value, so that 0.2 g of fat
 * against 0.4 g reads as a rounding difference rather than as being twice as
 * fatty.
 *
 * <p>Five components are compared and three are not. Energy and protein carry
 * the most weight because they are what a swap has to preserve; fat and
 * carbohydrate follow; fibre breaks ties. Sugars are published for only 205 of
 * the 957 foods, so counting them would mostly mean counting nothing, and
 * sodium says how a food was canned rather than what it is — a fresh food and
 * its tinned self are the same swap.
 *
 * <p><strong>Only components both foods publish are compared.</strong> A
 * missing figure is not zero, so it is not distance either; and when fewer than
 * {@link #FEWEST_COMPARED} components are shared, the answer is null rather
 * than a score resting on one number.
 */
@Component
public class NutritionSimilarity implements INutritionSimilarity {

    /** Below this, a score would be a single component wearing a percentage. */
    private static final int FEWEST_COMPARED = 2;

    private static final int SCALE = 8;

    /**
     * What is compared, how much it counts, and the figure below which a
     * difference is noise rather than a difference.
     */
    private static final List<Compared> COMPONENTS = List.of(
            new Compared(NutritionDto::energyKcal, 3, new BigDecimal("20")),
            new Compared(NutritionDto::proteinG, 3, new BigDecimal("5")),
            new Compared(NutritionDto::fatG, 2, new BigDecimal("5")),
            new Compared(NutritionDto::carbohydratesG, 2, new BigDecimal("5")),
            new Compared(NutritionDto::fiberG, 1, new BigDecimal("2")));

    @Override
    public Integer score(NutritionDto reference, NutritionDto candidate) {
        if (reference == null || candidate == null) {
            return null;
        }

        BigDecimal weighted = BigDecimal.ZERO;
        int weights = 0;
        int compared = 0;

        for (Compared component : COMPONENTS) {
            BigDecimal a = component.of().apply(reference);
            BigDecimal b = component.of().apply(candidate);
            if (a == null || b == null) {
                continue;
            }
            compared++;
            weights += component.weight();
            weighted = weighted.add(
                    distance(a, b, component.floor()).multiply(BigDecimal.valueOf(component.weight())));
        }

        if (compared < FEWEST_COMPARED) {
            return null;
        }
        BigDecimal apart = weighted.divide(BigDecimal.valueOf(weights), SCALE, RoundingMode.HALF_UP);
        return BigDecimal.ONE.subtract(apart)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /**
     * How far apart two figures of the same component are, 0 for identical and
     * 1 for as far apart as this measure goes. Dividing by the larger of the
     * two keeps the answer a share; dividing by the floor instead, when both
     * are small, keeps a gram of difference from reading as a total mismatch.
     */
    private static BigDecimal distance(BigDecimal a, BigDecimal b, BigDecimal floor) {
        BigDecimal against = a.max(b).max(floor);
        if (against.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return a.subtract(b).abs()
                .divide(against, SCALE, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
    }

    /** One component of the comparison: how to read it, and what it counts for. */
    private record Compared(Function<NutritionDto, BigDecimal> of, int weight, BigDecimal floor) {
    }
}
