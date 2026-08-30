package com.fdiet.journal.exception;

/** Thrown when a journal row is asked for by an id nothing carries. */
public class JournalEntryNotFoundException extends RuntimeException {

    private JournalEntryNotFoundException(String message) {
        super(message);
    }

    public static JournalEntryNotFoundException extra(Long id) {
        return new JournalEntryNotFoundException("Extra food not found: " + id);
    }

    /** The slot is real but nothing has been scored there. */
    public static JournalEntryNotFoundException score(String slot) {
        return new JournalEntryNotFoundException("No score for slot: " + slot);
    }
}
