package com.fdiet.food.exception;

public class CompositionFoodNotFoundException extends RuntimeException {

    public CompositionFoodNotFoundException(Long id) {
        super("No CIQUAL or BLS food with id " + id);
    }
}
