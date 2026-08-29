package com.fdiet.diet.dto;

import java.util.List;

/**
 * One sheet of a workbook as raw text, the way
 * {@link com.fdiet.food.helpers.DataReader} hands back the CSV: rows of cells,
 * nothing interpreted. Short rows are padded, so every row has the same width
 * and a column index always means the same day.
 */
public record SheetGridDto(String name, List<List<String>> rows) {

    /** The cell at that position, or an empty string when the sheet stops short. */
    public String cell(int row, int column) {
        if (row < 0 || row >= rows.size()) {
            return "";
        }
        List<String> cells = rows.get(row);
        return column < 0 || column >= cells.size() ? "" : cells.get(column);
    }
}
