package com.fdiet.reference.service;

import com.fdiet.reference.domain.MeasureUser;

/**
 * How many rows of another context a household measure weighs.
 *
 * <p>The dependency runs from the diet and the journal to the reference tables,
 * never back, so the reference module cannot ask their services. Instead it
 * declares this, and the service that owns each table implements it: the
 * recipe service for ingredients, the journal for extras. One count query each.
 */
public interface IMeasureUsageCounter {

    MeasureUser user();

    long countUsing(Long measureId);
}
