package com.fdiet.reference.service;

import com.fdiet.reference.dto.ReferenceSyncSummaryDto;

/**
 * Reads the reference CSVs under {@code reference-data/} and hands their rows
 * to {@link IReferenceService}, the way the composition database's importer
 * hands its rows to the service that owns the table.
 */
public interface IReferenceImportService {

    /** Loads every reference file. Safe to re-run: codes are written over, never duplicated. */
    ReferenceSyncSummaryDto sync();
}
