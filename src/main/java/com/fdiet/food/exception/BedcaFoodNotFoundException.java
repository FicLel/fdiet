package com.fdiet.food.exception;

public class BedcaFoodNotFoundException extends RuntimeException {

    public BedcaFoodNotFoundException(Long id) {
        super("No composition-database food with id " + id);
    }

    /** For a food asked for by the name a diet wrote, which is matched exactly. */
    public BedcaFoodNotFoundException(String name) {
        super("No composition-database food named \"" + name + "\"");
    }
}
