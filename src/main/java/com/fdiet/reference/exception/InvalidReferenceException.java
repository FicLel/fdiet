package com.fdiet.reference.exception;

/** A reference request, or a reference data file, that cannot be taken as it is. */
public class InvalidReferenceException extends RuntimeException {

    public InvalidReferenceException(String message) {
        super(message);
    }
}
