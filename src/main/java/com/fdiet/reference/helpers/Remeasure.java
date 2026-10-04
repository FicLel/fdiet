package com.fdiet.reference.helpers;

import com.fdiet.reference.model.ReferenceFoodMeasure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Applies a fresh choice of measure to stored rows (FD-054): the one step the
 * owners of recipe ingredients and of extras share once the reference module has
 * answered, so "re-weighed" means the same in both answers.
 */
public final class Remeasure {

    private Remeasure() {
    }

    /**
     * Points each row at its chosen measure — null where the rule now attaches
     * nothing — and answers the rows whose measure that changed. O(n).
     *
     * @param chosen  the rule's answer for each row, lined up with {@code rows}
     * @param current the row's measure now
     * @param attach  points a row at a measure, as the rule's choice (never a pick)
     */
    public static <T> List<T> apply(List<T> rows, List<ReferenceFoodMeasure> chosen,
                                    Function<T, ReferenceFoodMeasure> current,
                                    BiConsumer<T, ReferenceFoodMeasure> attach) {
        List<T> changed = new ArrayList<>();
        for (int at = 0; at < rows.size(); at++) {
            T row = rows.get(at);
            ReferenceFoodMeasure measure = chosen.get(at);
            if (!Objects.equals(idOf(current.apply(row)), idOf(measure))) {
                attach.accept(row, measure);
                changed.add(row);
            }
        }
        return changed;
    }

    private static Long idOf(ReferenceFoodMeasure measure) {
        return measure == null ? null : measure.getId();
    }
}
