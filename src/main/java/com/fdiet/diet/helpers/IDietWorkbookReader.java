package com.fdiet.diet.helpers;

import com.fdiet.diet.dto.SheetGridDto;

import java.io.InputStream;

/** Reads one sheet of a diet workbook as raw text, interpreting nothing. */
public interface IDietWorkbookReader {

    /**
     * @param sheetName the sheet to read, or null/blank for the first one
     * @throws com.fdiet.diet.exception.InvalidDietException when the workbook
     *         cannot be read or carries no such sheet
     */
    SheetGridDto read(InputStream workbook, String sheetName);
}
