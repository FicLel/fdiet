package com.fdiet.food.repository;

import com.fdiet.food.model.FoodItem;

import java.util.List;

/** Bulk insert path used by the CSV import. */
public interface FoodItemBatchRepository {

    /**
     * Persists every item, flushing every {@code batchSize} rows so the JDBC
     * driver can send them as batched inserts.
     *
     * @return the number of rows persisted
     */
    int insertBatch(List<FoodItem> items, int batchSize);
}
