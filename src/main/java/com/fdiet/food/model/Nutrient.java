package com.fdiet.food.model;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The composition components fdiet keeps, in one place: the key they travel
 * under, the column pair they are stored in, and how to read and write them on
 * any food that carries them ({@link CompositionFigures}, implemented by
 * {@link CompositionFood} for CIQUAL and BLS alike).
 *
 * <p>The importers, the mappers and the transport shape all loop over this
 * rather than repeating fourteen names three times. Adding a component the
 * sources already carry is a constant here, a field on the entity and a column pair
 * in a migration; each table reader maps it to its source's own column.
 */
public enum Nutrient {

    ENERGY("energy", "energy",
            CompositionFigures::getEnergy, CompositionFigures::setEnergy),
    PROTEIN("protein", "protein",
            CompositionFigures::getProtein, CompositionFigures::setProtein),
    FAT("fat", "fat",
            CompositionFigures::getFat, CompositionFigures::setFat),
    SATURATED_FAT("saturatedFat", "saturated_fat",
            CompositionFigures::getSaturatedFat, CompositionFigures::setSaturatedFat),
    CARBOHYDRATES("carbohydrates", "carbohydrates",
            CompositionFigures::getCarbohydrates, CompositionFigures::setCarbohydrates),
    SUGARS("sugars", "sugars",
            CompositionFigures::getSugars, CompositionFigures::setSugars),
    FIBER("fiber", "fiber",
            CompositionFigures::getFiber, CompositionFigures::setFiber),
    WATER("water", "water",
            CompositionFigures::getWater, CompositionFigures::setWater),
    SODIUM("sodium", "sodium",
            CompositionFigures::getSodium, CompositionFigures::setSodium),
    POTASSIUM("potassium", "potassium",
            CompositionFigures::getPotassium, CompositionFigures::setPotassium),
    CALCIUM("calcium", "calcium",
            CompositionFigures::getCalcium, CompositionFigures::setCalcium),
    IRON("iron", "iron",
            CompositionFigures::getIron, CompositionFigures::setIron),
    CHOLESTEROL("cholesterol", "cholesterol",
            CompositionFigures::getCholesterol, CompositionFigures::setCholesterol),
    VITAMIN_C("vitaminC", "vitamin_c",
            CompositionFigures::getVitaminC, CompositionFigures::setVitaminC);

    /** The suffix of the column that holds a component's unit: {@code energy_unit}. */
    public static final String UNIT_COLUMN_SUFFIX = "_unit";

    private final String key;
    private final String column;
    private final Function<CompositionFigures, NutrientValue> getter;
    private final BiConsumer<CompositionFigures, NutrientValue> setter;

    Nutrient(String key,
             String column,
             Function<CompositionFigures, NutrientValue> getter,
             BiConsumer<CompositionFigures, NutrientValue> setter) {
        this.key = key;
        this.column = column;
        this.getter = getter;
        this.setter = setter;
    }

    /** What it is called in the JSON and in a parsed CSV row. */
    public String key() {
        return key;
    }

    /**
     * The column the value is stored in; the unit sits beside it in
     * {@code column + }{@link #UNIT_COLUMN_SUFFIX}. The pair in
     * {@code composition_foods}.
     */
    public String column() {
        return column;
    }

    public NutrientValue of(CompositionFigures food) {
        return getter.apply(food);
    }

    public void set(CompositionFigures food, NutrientValue value) {
        setter.accept(food, value);
    }
}
