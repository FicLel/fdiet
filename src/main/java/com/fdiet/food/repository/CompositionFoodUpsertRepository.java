package com.fdiet.food.repository;

import com.fdiet.food.dto.CompositionFoodRowDto;

import java.util.List;

/** Bulk write path of the composition sync. */
public interface CompositionFoodUpsertRepository {

    /**
     * Inserts every row whose {@code (source, source_code)} is new and writes
     * over every row already stored, keeping its id: one
     * {@code INSERT … ON DUPLICATE KEY UPDATE} sent in JDBC batches of
     * {@code batchSize}, so ten thousand rows are a few dozen round trips rather
     * than ten thousand.
     */
    void upsertAll(List<CompositionFoodRowDto> rows, int batchSize);
}
