package com.fdiet.food.repository;

import com.fdiet.food.model.FoodItem;
import jakarta.persistence.EntityManager;

import java.util.List;

class FoodItemBatchRepositoryImpl implements FoodItemBatchRepository {

    private final EntityManager entityManager;

    FoodItemBatchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public int insertBatch(List<FoodItem> items, int batchSize) {
        int persisted = 0;
        for (FoodItem item : items) {
            entityManager.persist(item);
            // Flush only: clearing here would detach the Category/SubCategory
            // instances the remaining items still point at.
            if (++persisted % batchSize == 0) {
                entityManager.flush();
            }
        }
        entityManager.flush();
        return persisted;
    }
}
