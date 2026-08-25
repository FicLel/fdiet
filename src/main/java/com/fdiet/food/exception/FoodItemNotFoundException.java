package com.fdiet.food.exception;

public class FoodItemNotFoundException extends RuntimeException {

    public FoodItemNotFoundException(Long id) {
        super("No food item with id " + id);
    }
}
