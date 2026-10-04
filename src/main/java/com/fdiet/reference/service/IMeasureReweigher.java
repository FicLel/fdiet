package com.fdiet.reference.service;

import com.fdiet.reference.domain.MeasureScope;
import com.fdiet.reference.domain.MeasureUser;

/**
 * Chooses again the household measure of the rows a new or changed criterion
 * reaches, where no person picked it (FD-054).
 *
 * <p>Like {@link IMeasureUsageCounter}, a port: the rows are the diet's recipe
 * ingredients and the journal's extras, and the reference module may not call
 * those services, so it declares the question and the owner of each table
 * answers it. Each answer runs the same rule a publish does, in a constant
 * number of queries whatever the number of rows.
 */
public interface IMeasureReweigher {

    MeasureUser user();

    /**
     * Re-chooses the measure of every row in scope whose measure nobody picked,
     * and answers how many now have a different one.
     */
    int reweigh(MeasureScope scope);
}
