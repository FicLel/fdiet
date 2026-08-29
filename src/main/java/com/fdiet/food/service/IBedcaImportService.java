package com.fdiet.food.service;

import com.fdiet.food.dto.BedcaSyncSummaryDto;

/**
 * Brings the Spanish food composition database into the catalogue.
 *
 * <p>It owns no repository: it parses bedca_foods.csv and hands typed rows to
 * the service that owns the table, the way {@link FoodImportService} does for
 * the branded catalogue.
 */
public interface IBedcaImportService {

    /**
     * The attribution that has to travel with these figures, and appear on any
     * screen that shows them. See BEDCA-ATTRIBUTION.txt.
     */
    String SOURCE =
            "AESAN/BEDCA Base de Datos Española de Composición de Alimentos v1.0 (2010)";

    /** Reads the file and stores what it holds. Running it again is safe. */
    BedcaSyncSummaryDto sync();
}
