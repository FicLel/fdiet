package com.fdiet.patient.exception;

/**
 * Thrown when a patient breaks an invariant the bean validation constraints
 * cannot express — a name somebody else already holds, or a deletion that would
 * take a stored diet with it.
 */
public class InvalidPatientException extends RuntimeException {

    public InvalidPatientException(String message) {
        super(message);
    }
}
