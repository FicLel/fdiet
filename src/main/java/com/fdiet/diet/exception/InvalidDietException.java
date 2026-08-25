package com.fdiet.diet.exception;

/** Thrown when a diet breaks an invariant the bean validation constraints cannot express. */
public class InvalidDietException extends RuntimeException {

    public InvalidDietException(String message) {
        super(message);
    }
}
