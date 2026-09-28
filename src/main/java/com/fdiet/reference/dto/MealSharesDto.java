package com.fdiet.reference.dto;

import java.util.List;

/**
 * The meal energy shares a profile is read against, and where they come from.
 *
 * <p>{@code borrowed} is true when the profile's own source publishes none and
 * the shares are another band's; {@code note} then says so in words, and it is
 * shown wherever the shares are drawn. Adults read against AESAN 2022 borrow the
 * AESAN/MEC 2010 school distribution this way, because no Spanish tier-1
 * document states one for adults.
 */
public record MealSharesDto(
        String fromProfileCode,
        String fromProfileLabel,
        String sourceCode,
        String sourceShortName,
        boolean borrowed,
        String note,
        String pageRef,
        List<MealShareDto> shares) {
}
