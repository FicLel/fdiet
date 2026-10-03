package com.fdiet.food.service;

import com.fdiet.food.dto.CompositionSyncSummaryDto;

/**
 * Brings the open composition tables (CIQUAL 2025, BLS 4.0) and fdiet's
 * Spanish-name crosswalk into {@code composition_foods}.
 *
 * <p>It owns no repository: it reads the snapshots committed under
 * {@code reference-data/composition/} and hands typed rows to the service that
 * owns the table, the way {@link IBedcaImportService} does for BEDCA.
 */
public interface ICompositionImportService {

    /**
     * Reads every file and stores what they hold. Safe to re-run: a food is
     * found again by its source and code and written over in place. The answer
     * carries both sources' attribution strings.
     */
    CompositionSyncSummaryDto sync();
}
