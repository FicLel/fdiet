package com.fdiet.reference.service;

import com.fdiet.reference.dto.ReferenceProfileDto;

import java.util.Comparator;

/** One population band as the in-memory snapshot holds it, with its source's ordering fields. */
record PopulationView(
        String code, String label, String sourceCode, String sourceShortName, int sourceTier,
        Integer sourceYear, Integer ageMinMonths, Integer ageMaxMonths, String context,
        String mealSharesFrom, String mealSharesNote, boolean selectable, Long id) {

    /** Nearest tier first, then the newest document, then the order it was loaded in. */
    static final Comparator<PopulationView> ORDER = Comparator
            .comparingInt(PopulationView::sourceTier)
            .thenComparing(view -> view.sourceYear() == null ? 0 : -view.sourceYear())
            .thenComparing(PopulationView::id);

    boolean covers(int ageMonths) {
        if (ageMinMonths == null && ageMaxMonths == null) {
            return false;
        }
        return (ageMinMonths == null || ageMonths >= ageMinMonths)
                && (ageMaxMonths == null || ageMonths <= ageMaxMonths);
    }

    ReferenceProfileDto profile(boolean suggested) {
        return new ReferenceProfileDto(code, label, sourceCode, sourceShortName, ageMinMonths,
                ageMaxMonths, context, selectable, suggested);
    }
}
