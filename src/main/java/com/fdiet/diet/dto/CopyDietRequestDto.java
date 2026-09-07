package com.fdiet.diet.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Where a week is being copied to.
 *
 * <p>The copy is a new diet of its own from the moment it is written: it carries
 * the days, the dishes, the sentences they were typed as and the food each
 * ingredient was matched to, and nothing else. Editing either one afterwards
 * leaves the other alone, and the journal is <em>not</em> copied — what one
 * patient thought of a plate is not what another thought of it.
 *
 * @param patientId whose diet the copy becomes; it takes their active slot and
 *                  archives whatever was in it. The source patient is allowed:
 *                  that is how this week becomes the starting point of the next.
 * @param name      what to call the copy, or null to keep the source's name
 * @param startedOn the day the copy starts, or null for today
 */
public record CopyDietRequestDto(
        @NotNull Long patientId,
        String name,
        LocalDate startedOn) {
}
