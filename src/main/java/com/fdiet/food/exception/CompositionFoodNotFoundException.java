package com.fdiet.food.exception;

public class CompositionFoodNotFoundException extends RuntimeException {

    public CompositionFoodNotFoundException(Long id) {
        super("No CIQUAL or BLS food with id " + id);
    }

    /** No food carries this Spanish name or alias exactly. */
    public CompositionFoodNotFoundException(String name) {
        super("No CIQUAL or BLS food is named \"" + name + "\" in the Spanish crosswalk");
    }
}
