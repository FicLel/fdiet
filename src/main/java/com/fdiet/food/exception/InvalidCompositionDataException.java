package com.fdiet.food.exception;

/**
 * A composition file that cannot be loaded as it stands: a column the reader
 * needs is missing, or the crosswalk gives one Spanish name to two foods without
 * saying which answers it. Nothing is written when this is thrown.
 */
public class InvalidCompositionDataException extends RuntimeException {

    public InvalidCompositionDataException(String message) {
        super(message);
    }
}
