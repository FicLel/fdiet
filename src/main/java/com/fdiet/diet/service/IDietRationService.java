package com.fdiet.diet.service;

import com.fdiet.diet.dto.DietRationsDto;
import com.fdiet.diet.model.DietPlan;

/**
 * Reads a loaded week against a reference profile: rations per group, the
 * recommendations they are counted against, meal energy shares and exchange
 * counts. It owns no repository and stores nothing — every figure is derived on
 * read, like a total.
 */
public interface IDietRationService {

    /**
     * @param plan        a diet with its week already loaded
     * @param profileCode the profile to count against, or null for the diet's own
     */
    DietRationsDto account(DietPlan plan, String profileCode);
}
