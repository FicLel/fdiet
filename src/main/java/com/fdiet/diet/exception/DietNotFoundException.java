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

    /** A patient has no active diet until one has been written for them. */
    public static DietNotFoundException noActiveDiet(Long patientId) {
        return new DietNotFoundException("Patient " + patientId + " has no active diet");
    }
}
