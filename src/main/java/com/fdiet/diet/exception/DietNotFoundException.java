package com.fdiet.diet.exception;

/** Thrown when a diet, or one of its rows, is asked for by an id nothing carries. */
public class DietNotFoundException extends RuntimeException {

    private DietNotFoundException(String message) {
        super(message);
    }

    public static DietNotFoundException diet(Long id) {
        return new DietNotFoundException("Diet not found: " + id);
    }

    public static DietNotFoundException ingredient(Long id) {
        return new DietNotFoundException("Diet ingredient not found: " + id);
    }

    /** There is no active diet until the first one has been set up. */
    public static DietNotFoundException noActiveDiet() {
        return new DietNotFoundException("There is no active diet");
    }
}
