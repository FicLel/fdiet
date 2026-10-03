package com.fdiet.food.dto;

import java.util.List;

/**
 * What a sync of the open composition tables did.
 *
 * <p>{@code attributions} are the CIQUAL and BLS attribution strings their
 * licences require wherever the figures are shown; they are returned here so no
 * caller stores the data without having been handed them.
 *
 * <p>{@code linked} is how many foods the Spanish-name crosswalk names, and
 * {@code linksUnmatched} the crosswalk rows naming a code neither table holds —
 * skipped with their reason rather than failing the sync. {@code withoutEnergy}
 * counts the foods stored with a blank energy.
 */
public record CompositionSyncSummaryDto(
        int ciqualRows,
        int blsRows,
        int skipped,
        int inserted,
        int updated,
        int linked,
        List<String> linksUnmatched,
        int withoutEnergy,
        List<String> attributions) {
}
