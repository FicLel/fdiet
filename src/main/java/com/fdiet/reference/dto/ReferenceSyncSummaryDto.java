package com.fdiet.reference.dto;

import java.util.List;

/**
 * What a reference sync did, table by table, and every row it could not load
 * with the reason. {@code attributions} is every source line the loaded figures
 * may not be shown without.
 */
public record ReferenceSyncSummaryDto(
        List<TableSync> tables,
        List<String> skipped,
        List<String> attributions) {

    /** One table's rows: read from the CSVs, then inserted or written over. */
    public record TableSync(String table, int rows, int inserted, int updated) {
    }
}
