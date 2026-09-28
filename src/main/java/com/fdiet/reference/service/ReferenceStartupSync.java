package com.fdiet.reference.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads the reference CSVs when the application starts.
 *
 * <p>Unlike the two food catalogues, which are large and imported on request,
 * the reference layer is a few hundred reviewed rows that the editor cannot
 * offer a profile without — so they are brought in on every start, and a
 * changed CSV is picked up by a restart. The sync is idempotent. A failure is
 * logged rather than stopping the application: the diets still read and write
 * without it, and {@code POST /api/reference/sync} says what is wrong.
 *
 * <p>Turned off with {@code fdiet.reference.sync-on-startup=false}.
 */
@Component
@ConditionalOnProperty(name = "fdiet.reference.sync-on-startup", havingValue = "true",
        matchIfMissing = true)
public class ReferenceStartupSync {

    private static final Logger log = LoggerFactory.getLogger(ReferenceStartupSync.class);

    private final IReferenceImportService importService;

    public ReferenceStartupSync(IReferenceImportService importService) {
        this.importService = importService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sync() {
        try {
            importService.sync();
        } catch (RuntimeException e) {
            log.error("Reference data was not loaded at startup: {}", e.getMessage());
        }
    }
}
