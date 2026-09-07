package com.fdiet.patient.exception;

/** Thrown when a patient is asked for by an id nothing carries. */
public class PatientNotFoundException extends RuntimeException {

    private PatientNotFoundException(String message) {
        super(message);
    }

    public static PatientNotFoundException patient(Long id) {
        return new PatientNotFoundException("Patient not found: " + id);
    }
}
