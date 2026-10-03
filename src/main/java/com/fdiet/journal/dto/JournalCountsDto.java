package com.fdiet.journal.dto;

/**
 * How much of the patient's record hangs off one diet — what a confirm has to
 * say before the diet is deleted and its journal with it.
 *
 * @param scored the plates the patient has scored
 * @param extras the off-plan entries logged against the diet
 */
public record JournalCountsDto(Long dietId, long scored, long extras) {
}
