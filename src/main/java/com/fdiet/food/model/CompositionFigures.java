package com.fdiet.food.model;

/**
 * The fourteen published figures a composition food carries, each as a value
 * and its own unit.
 *
 * <p>Two tables hold them: {@code bedca_foods} ({@link BedcaFood}) and
 * {@code composition_foods} ({@link CompositionFood}, CIQUAL and BLS). Both
 * entities implement this so {@link Nutrient}, the unit arithmetic and the
 * mappers loop over one shape rather than one per table. The accessors are the
 * ones Lombok generates on each entity.
 */
public interface CompositionFigures {

    NutrientValue getEnergy();

    void setEnergy(NutrientValue value);

    NutrientValue getProtein();

    void setProtein(NutrientValue value);

    NutrientValue getFat();

    void setFat(NutrientValue value);

    NutrientValue getSaturatedFat();

    void setSaturatedFat(NutrientValue value);

    NutrientValue getCarbohydrates();

    void setCarbohydrates(NutrientValue value);

    NutrientValue getSugars();

    void setSugars(NutrientValue value);

    NutrientValue getFiber();

    void setFiber(NutrientValue value);

    NutrientValue getWater();

    void setWater(NutrientValue value);

    NutrientValue getSodium();

    void setSodium(NutrientValue value);

    NutrientValue getPotassium();

    void setPotassium(NutrientValue value);

    NutrientValue getCalcium();

    void setCalcium(NutrientValue value);

    NutrientValue getIron();

    void setIron(NutrientValue value);

    NutrientValue getCholesterol();

    void setCholesterol(NutrientValue value);

    NutrientValue getVitaminC();

    void setVitaminC(NutrientValue value);
}
