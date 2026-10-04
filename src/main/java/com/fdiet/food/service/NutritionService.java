package com.fdiet.food.service;

import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.model.CompositionFigures;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.model.NutrientValue;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * The unit arithmetic, and nothing else.
 *
 * <p>A figure whose unit is not one this understands comes back null rather
 * than as a number in the wrong scale. That is the whole point of keeping the
 * unit beside the value: a silent misreading of milligrams as grams is a
 * thousandfold error in someone's diet.
 */
@Service
public class NutritionService implements INutritionService {

    /** The kilojoules in one (thermochemical) kilocalorie. */
    private static final BigDecimal KJ_PER_KCAL = new BigDecimal("4.184");

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    /**
     * Grams of salt per gram of sodium, the factor food labelling is written
     * with. Only used for the branded catalogue, whose rows declare salt and
     * leave sodium null.
     */
    private static final BigDecimal SALT_PER_SODIUM = new BigDecimal("2.5");

    /** Enough places that a milligram converted to grams is not rounded away. */
    private static final int SCALE = 6;

    @Override
    public NutritionDto per100g(CompositionFigures food) {
        if (food == null) {
            return NutritionDto.EMPTY;
        }
        return new NutritionDto(
                kcal(food.getEnergy()),
                grams(food.getProtein()),
                grams(food.getFat()),
                grams(food.getSaturatedFat()),
                grams(food.getCarbohydrates()),
                grams(food.getSugars()),
                grams(food.getFiber()),
                milligrams(food.getSodium()));
    }

    @Override
    public NutritionDto per100g(FoodItem item) {
        if (item == null) {
            return NutritionDto.EMPTY;
        }
        return new NutritionDto(
                item.getEnergyKcal() != null ? item.getEnergyKcal() : fromKj(item.getEnergyKj()),
                item.getProteinsG(),
                item.getFatG(),
                item.getSaturatedFatG(),
                item.getCarbohydratesG(),
                item.getSugarsG(),
                item.getFiberG(),
                sodiumMgOf(item));
    }

    /**
     * The label declares salt far more often than sodium, so the salt figure
     * stands in for it when there is one.
     */
    private BigDecimal sodiumMgOf(FoodItem item) {
        if (item.getSodiumG() != null) {
            return item.getSodiumG().multiply(THOUSAND);
        }
        if (item.getSaltG() == null) {
            return null;
        }
        return item.getSaltG().divide(SALT_PER_SODIUM, SCALE, RoundingMode.HALF_UP)
                .multiply(THOUSAND);
    }

    private BigDecimal kcal(NutrientValue nutrient) {
        BigDecimal value = valueOf(nutrient);
        if (value == null) {
            return null;
        }
        return switch (unitOf(nutrient)) {
            case "kcal" -> value;
            case "kj" -> fromKj(value);
            default -> null;
        };
    }

    private BigDecimal grams(NutrientValue nutrient) {
        BigDecimal value = valueOf(nutrient);
        if (value == null) {
            return null;
        }
        return switch (unitOf(nutrient)) {
            case "g" -> value;
            case "mg" -> value.divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
            case "ug", "µg" -> value.divide(MILLION, SCALE, RoundingMode.HALF_UP);
            default -> null;
        };
    }

    private BigDecimal milligrams(NutrientValue nutrient) {
        BigDecimal value = valueOf(nutrient);
        if (value == null) {
            return null;
        }
        return switch (unitOf(nutrient)) {
            case "mg" -> value;
            case "g" -> value.multiply(THOUSAND);
            case "ug", "µg" -> value.divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
            default -> null;
        };
    }

    private static BigDecimal fromKj(BigDecimal kilojoules) {
        return kilojoules == null
                ? null
                : kilojoules.divide(KJ_PER_KCAL, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal valueOf(NutrientValue nutrient) {
        return nutrient == null ? null : nutrient.getValue();
    }

    private static String unitOf(NutrientValue nutrient) {
        String unit = nutrient == null ? null : Texts.trimToNull(nutrient.getUnit());
        return unit == null ? "" : unit.toLowerCase(Locale.ROOT);
    }
}
