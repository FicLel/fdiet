package com.fdiet.food.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads CIQUAL and BLS when the application starts on an empty table.
 *
 * <p>Unlike the reference layer, which is re-read on every start because it is
 * a few hundred rows, the two tables are ten thousand rows read out of 15 MB
 * of spreadsheets: too slow to repeat on every start but needed before the
 * first search. So a fresh database
 * is filled on its first start, and a changed snapshot or crosswalk is brought
 * in by {@code POST /api/composition/sync}. A failure is logged rather than
 * stopping the application, as the reference sync does.
 *
 * <p>Turned off with {@code fdiet.composition.sync-on-startup=false}.
 */
@Component
@ConditionalOnProperty(name = "fdiet.composition.sync-on-startup", havingValue = "true",
        matchIfMissing = true)
public class CompositionStartupSync {

    /**
     * Runs before the reference sync ({@code ReferenceStartupSync}): reference rows
     * name composition foods, and on a fresh database they can only be keyed once
     * the foods are in.
     */
    public static final int ORDER = 0;

    private static final Logger log = LoggerFactory.getLogger(CompositionStartupSync.class);

    private final ICompositionImportService importService;
    private final ICompositionFoodService compositionFoodService;

    public CompositionStartupSync(ICompositionImportService importService,
                                  ICompositionFoodService compositionFoodService) {
        this.importService = importService;
        this.compositionFoodService = compositionFoodService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(ORDER)
    public void sync() {
        try {
            if (compositionFoodService.isEmpty()) {
                importService.sync();
            }
        } catch (RuntimeException e) {
            log.error("Composition data was not loaded at startup: {}", e.getMessage());
        }
    }
}
