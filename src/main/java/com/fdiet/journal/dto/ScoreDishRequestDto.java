package com.fdiet.journal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * What the patient thought of the plate, 1 to 5.
 *
 * <p>There is no zero. Taking a score back is a DELETE of the slot's score, not
 * a score of nothing — "I have no opinion" and "I thought it was worth zero"
 * are different answers and only one of them belongs in an average.
 */
public record ScoreDishRequestDto(
        @NotNull @Min(1) @Max(5) Integer score) {
}
