package com.fdiet.reference.exception;

public class ReferenceNotFoundException extends RuntimeException {

    public ReferenceNotFoundException(String message) {
        super(message);
    }

    public static ReferenceNotFoundException profile(String code) {
        return new ReferenceNotFoundException("No reference profile with code " + code
                + ". GET /api/reference/profiles lists them; POST /api/reference/sync loads them.");
    }

    public static ReferenceNotFoundException measure(Long id) {
        return new ReferenceNotFoundException("No household measure with id " + id);
    }
}
