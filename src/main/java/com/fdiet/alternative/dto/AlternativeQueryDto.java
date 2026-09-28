package com.fdiet.alternative.dto;

import com.fdiet.alternative.domain.EquivalenceBasis;

import java.math.BigDecimal;

/**
 * How an alternatives question is asked.
 *
 * @param limit       how many to hand back, best first
 * @param grams       the portion the diet prescribes, or null to answer per 100 g
 * @param sameFood    whether other preparations of the same food stay in the list
 * @param basis       what an equivalent portion holds constant; energy when null
 * @param profileCode a reference profile to express each equivalent portion in
 *                    rations of, or null for grams alone
 */
public record AlternativeQueryDto(
        int limit,
        BigDecimal grams,
        boolean sameFood,
        EquivalenceBasis basis,
        String profileCode) {

    public AlternativeQueryDto {
        basis = basis == null ? EquivalenceBasis.ENERGY : basis;
        profileCode = profileCode == null || profileCode.isBlank() ? null : profileCode.trim();
    }

    /** The question as it was first asked: by energy, in grams alone. */
    public static AlternativeQueryDto byEnergy(int limit, BigDecimal grams, boolean sameFood) {
        return new AlternativeQueryDto(limit, grams, sameFood, EquivalenceBasis.ENERGY, null);
    }
}
