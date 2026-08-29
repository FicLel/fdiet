package com.fdiet.food.exception;

public class BedcaFoodNotFoundException extends RuntimeException {

    public BedcaFoodNotFoundException(Long id) {
        super("No composition-database food with id " + id);
    }
}
