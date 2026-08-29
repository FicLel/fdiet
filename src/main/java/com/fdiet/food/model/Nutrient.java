package com.fdiet.food.model;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The composition components fdiet keeps, in one place: the key they travel
 * under, the code the source publishes them as, and how to read and write them
 * on a {@link BedcaFood}.
 *
 * <p>The importer, the mapper and the transport shape all loop over this rather
 * than repeating fourteen names three times. Adding a component the source
 * already carries — there are 33 more in bedca_foods.csv — is a constant here,
 * a field on the entity and a column pair in a migration.
 */
public enum Nutrient {

    ENERGY("energy", "ENERC", BedcaFood::getEnergy, BedcaFood::setEnergy),
    PROTEIN("protein", "PROT", BedcaFood::getProtein, BedcaFood::setProtein),
    FAT("fat", "FAT", BedcaFood::getFat, BedcaFood::setFat),
    SATURATED_FAT("saturatedFat", "FASAT", BedcaFood::getSaturatedFat, BedcaFood::setSaturatedFat),
    CARBOHYDRATES("carbohydrates", "CHO", BedcaFood::getCarbohydrates, BedcaFood::setCarbohydrates),
    SUGARS("sugars", "SUGAR", BedcaFood::getSugars, BedcaFood::setSugars),
    FIBER("fiber", "FIBT", BedcaFood::getFiber, BedcaFood::setFiber),
    WATER("water", "WATER", BedcaFood::getWater, BedcaFood::setWater),
    SODIUM("sodium", "NA", BedcaFood::getSodium, BedcaFood::setSodium),
    POTASSIUM("potassium", "K", BedcaFood::getPotassium, BedcaFood::setPotassium),
    CALCIUM("calcium", "CA", BedcaFood::getCalcium, BedcaFood::setCalcium),
    IRON("iron", "FE", BedcaFood::getIron, BedcaFood::setIron),
    CHOLESTEROL("cholesterol", "CHORL", BedcaFood::getCholesterol, BedcaFood::setCholesterol),
    VITAMIN_C("vitaminC", "VITC", BedcaFood::getVitaminC, BedcaFood::setVitaminC);

    private final String key;
    private final String code;
    private final Function<BedcaFood, NutrientValue> getter;
    private final BiConsumer<BedcaFood, NutrientValue> setter;

    Nutrient(String key,
             String code,
             Function<BedcaFood, NutrientValue> getter,
             BiConsumer<BedcaFood, NutrientValue> setter) {
        this.key = key;
        this.code = code;
        this.getter = getter;
        this.setter = setter;
    }

    /** What it is called in the JSON and in a parsed CSV row. */
    public String key() {
        return key;
    }

    /** The EuroFIR component code, which is the CSV's column name. */
    public String code() {
        return code;
    }

    public NutrientValue of(BedcaFood food) {
        return getter.apply(food);
    }

    public void set(BedcaFood food, NutrientValue value) {
        setter.accept(food, value);
    }
}
