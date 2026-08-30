package com.fdiet.journal.exception;

/**
 * Thrown when an entry breaks a rule the bean validation constraints cannot
 * express — pointing at both halves of the catalogue at once, or at a slot the
 * diet does not have.
 */
public class InvalidJournalEntryException extends RuntimeException {

    public InvalidJournalEntryException(String message) {
        super(message);
    }
}
