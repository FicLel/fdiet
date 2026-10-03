package com.fdiet.food.helpers;

import com.fdiet.food.exception.InvalidCompositionDataException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A header row, read so that a column is found by what it says rather than by
 * where it sits. Header cells are compared with every run of whitespace — the
 * line breaks a spreadsheet wraps a long header onto included — collapsed to one
 * space, so {@code "Protein\n(g/100g)"} reads as {@code "Protein (g/100g)"}.
 */
public final class SheetHeader {

    private final List<String> names;

    public SheetHeader(List<String> header) {
        List<String> normalised = new ArrayList<>(header.size());
        for (String cell : header) {
            normalised.add(cell == null ? "" : cell.replaceAll("\\s+", " ").strip());
        }
        this.names = List.copyOf(normalised);
    }

    /** The column whose header is exactly {@code name}, or -1. */
    public int indexOf(String name) {
        return names.indexOf(name);
    }

    /** The first column whose header passes {@code test}, or -1. */
    public int find(Predicate<String> test) {
        for (int at = 0; at < names.size(); at++) {
            if (test.test(names.get(at))) {
                return at;
            }
        }
        return -1;
    }

    /** The header text of a column, normalised. */
    public String name(int column) {
        return names.get(column);
    }

    /**
     * The column whose header is exactly {@code name}; a file without it cannot
     * be read at all, so it is refused rather than half loaded.
     */
    public int require(String name, String file) {
        int at = indexOf(name);
        if (at < 0) {
            throw new InvalidCompositionDataException(file + " has no column \"" + name + "\"");
        }
        return at;
    }

    /** The cell at {@code column}, or null when the column is unknown or the row is shorter. */
    public static String cell(List<String> row, int column) {
        return column < 0 || column >= row.size() ? null : row.get(column);
    }
}
