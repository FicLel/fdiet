package com.fdiet.alternative.domain;

/**
 * The families a food belongs to for the purpose of swapping it for another.
 *
 * <p>An alternative is only ever offered inside one of these. Grilled chicken
 * and boiled lettuce can be made to look alike on paper — take a small enough
 * portion of one and a large enough portion of the other and their figures
 * meet — but no nutritionist swaps them, so the arithmetic is never allowed to
 * cross a category. That is the whole reason this enum exists: nutritional
 * distance decides the <em>order</em> of a list, and the category decides who
 * is in it.
 *
 * <p>The split is culinary rather than botanical, because a diet is written by
 * a person cooking. A tomato is a fruit and sits in {@link #VEGETABLE}; a
 * potato sits in {@link #TUBER} rather than with the vegetables, because
 * neither is a plausible swap for the other.
 *
 * <p>These are <strong>derived, never stored</strong>. The composition database
 * publishes a group for only 182 of its 957 foods, so the name is the only
 * signal every food has; see {@code helpers/FoodCategoriser}. Nothing writes a
 * category back to {@code bedca_foods}, for the same reason nothing writes back
 * a kcal figure converted from kilojoules.
 */
public enum FoodCategory {

    MEAT("Carnes y derivados"),
    FISH("Pescados y mariscos"),
    EGG("Huevos"),
    DAIRY("Lácteos"),
    LEGUME("Legumbres"),
    NUT("Frutos secos y semillas"),
    CEREAL("Cereales y derivados"),
    TUBER("Tubérculos"),
    VEGETABLE("Verduras y hortalizas"),
    FRUIT("Frutas"),
    FAT_OIL("Grasas y aceites"),
    SWEET("Azúcares y dulces"),
    BEVERAGE("Bebidas"),
    CONDIMENT("Salsas y condimentos"),
    PREPARED_DISH("Platos preparados"),
    SUPPLEMENT("Suplementos y dietéticos");

    private final String label;

    FoodCategory(String label) {
        this.label = label;
    }

    /** What the category is called in Spanish, the language the diets are in. */
    public String label() {
        return label;
    }
}
