package com.fdiet.diet.dto;

import com.fdiet.diet.model.DietStatus;

import java.time.LocalDate;

/**
 * A diet without its week, for the history listing. Carrying the days there
 * would mean loading every meal of every archived diet to render a list of
 * names.
 */
public record DietSummaryDto(
        Long id,
        String name,
        DietStatus status,
        LocalDate startedOn,
        LocalDate endedOn) {
}
