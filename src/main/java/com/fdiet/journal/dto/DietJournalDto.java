package com.fdiet.journal.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A whole week's journal in one answer: every score, and every off-plan entry
 * grouped by the day it was eaten on.
 *
 * <p>One call because the screen that reads it draws the whole week at once —
 * a star on each of seventy cells and a day's extras beside its total — and a
 * request per cell would be seventy requests for at most seventy small rows.
 *
 * <p><b>The average travels with its count.</b> {@code averageScore} over four
 * scored plates looks exactly like one over seventy unless {@code scored} says
 * otherwise, and it is null rather than zero when nothing has been scored:
 * nobody has rated this week badly, they have not rated it.
 */
public record DietJournalDto(
        Long dietId,
        List<DishScoreDto> scores,
        List<DayExtrasDto> days,
        BigDecimal averageScore,
        int scored) {
}
