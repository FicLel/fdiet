package com.fdiet.food.model;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The composition components fdiet keeps, in one place: the key they travel
 * under, the code BEDCA publishes them as, the column pair they are stored in,
 * and how to read and write them on any food that carries them
 * ({@link CompositionFigures}: {@link BedcaFood} and {@link CompositionFood}).
 *
 * <p>The importers, the mappers and the transport shape all loop over this
 * rather than repeating fourteen names three times. Adding a component the
 * sources already carry is a constant here, a field on each entity and a column
 * pair in a migration.
 */
public enum Nutrient {

    ENERGY("energy", "ENERC", "energy",
            CompositionFigures::getEnergy, CompositionFigures::setEnergy),
    PROTEIN("protein", "PROT", "protein",
            CompositionFigures::getProtein, CompositionFigures::setProtein),
    FAT("fat", "FAT", "fat",
            CompositionFigures::getFat, CompositionFigures::setFat),
    SATURATED_FAT("saturatedFat", "FASAT", "saturated_fat",
            CompositionFigures::getSaturatedFat, CompositionFigures::setSaturatedFat),
    CARBOHYDRATES("carbohydrates", "CHO", "carbohydrates",
            CompositionFigures::getCarbohydrates, CompositionFigures::setCarbohydrates),
    SUGARS("sugars", "SUGAR", "sugars",
            CompositionFigures::getSugars, CompositionFigures::setSugars),
    FIBER("fiber", "FIBT", "fiber",
            CompositionFigures::getFiber, CompositionFigures::setFiber),
    WATER("water", "WATER", "water",
            CompositionFigures::getWater, CompositionFigures::setWater),
    SODIUM("sodium", "NA", "sodium",
            CompositionFigures::getSodium, CompositionFigures::setSodium),
    POTASSIUM("potassium", "K", "potassium",
            CompositionFigures::getPotassium, CompositionFigures::setPotassium),
    CALCIUM("calcium", "CA", "calcium",
            CompositionFigures::getCalcium, CompositionFigures::setCalcium),
    IRON("iron", "FE", "iron",
            CompositionFigures::getIron, CompositionFigures::setIron),
    CHOLESTEROL("cholesterol", "CHORL", "cholesterol",
            CompositionFigures::getCholesterol, CompositionFigures::setCholesterol),
    VITAMIN_C("vitaminC", "VITC", "vitamin_c",
            CompositionFigures::getVitaminC, CompositionFigures::setVitaminC);

    /** The suffix of the column that holds a component's unit: {@code energy_unit}. */
    public static final String UNIT_COLUMN_SUFFIX = "_unit";

    private final String key;
    private final String code;
    private final String column;
    private final Function<CompositionFigures, NutrientValue> getter;
    private final BiConsumer<CompositionFigures, NutrientValue> setter;

    Nutrient(String key,
             String code,
             String column,
             Function<CompositionFigures, NutrientValue> getter,
             BiConsumer<CompositionFigures, NutrientValue> setter) {
        this.key = key;
        this.code = code;
        this.column = column;
        this.getter = getter;
        this.setter = setter;
    }

    /** What it is called in the JSON and in a parsed CSV row. */
    public String key() {
        return key;
    }

    /** The EuroFIR component code, which is bedca_foods.csv's column name. */
    public String code() {
        return code;
    }

    /**
     * The column the value is stored in; the unit sits beside it in
     * {@code column + }{@link #UNIT_COLUMN_SUFFIX}. The same pair in
     * {@code bedca_foods} and {@code composition_foods}.
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
