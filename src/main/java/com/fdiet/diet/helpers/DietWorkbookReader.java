package com.fdiet.diet.helpers;

import com.fdiet.diet.dto.SheetGridDto;
import com.fdiet.diet.exception.InvalidDietException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Hands one sheet back as a rectangle of strings, the way
 * {@link com.fdiet.food.helpers.DataReader} hands back the CSV: every cell is
 * text, nothing is interpreted, and the rows all have the same width so a
 * column index means the same day on every one of them.
 *
 * <p>A merged region reads as its top-left value in every cell it covers. The
 * workbook uses merges for the banner rows that head a meal ({@code Comida},
 * {@code Cena}), and that is how the importer recognises them.
 */
@Component
public class DietWorkbookReader implements IDietWorkbookReader {

    /** Enough for any hand-written diet, and a cap on what a bad upload can allocate. */
    private static final int MAX_ROWS = 200;
    private static final int MAX_COLUMNS = 60;

    private final DataFormatter formatter = new DataFormatter();

    @Override
    public SheetGridDto read(InputStream workbookStream, String sheetName) {
        try (Workbook workbook = WorkbookFactory.create(workbookStream)) {
            Sheet sheet = sheetOf(workbook, sheetName);
            return new SheetGridDto(sheet.getSheetName(), grid(sheet));
        } catch (IOException e) {
            throw new InvalidDietException("The workbook could not be read: " + e.getMessage());
        }
    }

    private Sheet sheetOf(Workbook workbook, String sheetName) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new InvalidDietException("The workbook has no sheets");
        }
        if (!StringUtils.hasText(sheetName)) {
            return workbook.getSheetAt(0);
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet.getSheetName().equalsIgnoreCase(sheetName.trim())) {
                return sheet;
            }
        }
        throw new InvalidDietException("The workbook has no sheet named " + sheetName);
    }

    private List<List<String>> grid(Sheet sheet) {
        int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS - 1);
        int width = widthOf(sheet, lastRow);

        List<List<String>> rows = new ArrayList<>(lastRow + 1);
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            List<String> cells = new ArrayList<>(width);
            for (int c = 0; c < width; c++) {
                Cell cell = row == null ? null : row.getCell(c);
                cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
            }
            rows.add(cells);
        }
        spreadMergedCells(sheet, rows, width);
        return List.copyOf(rows);
    }

    private int widthOf(Sheet sheet, int lastRow) {
        int width = 0;
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                width = Math.max(width, row.getLastCellNum());
            }
        }
        return Math.min(width, MAX_COLUMNS);
    }

    /** Copies the value of each merged region into every cell it covers. */
    private void spreadMergedCells(Sheet sheet, List<List<String>> rows, int width) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            int firstRow = region.getFirstRow();
            int firstColumn = region.getFirstColumn();
            if (firstRow >= rows.size() || firstColumn >= width) {
                continue;
            }
            String value = rows.get(firstRow).get(firstColumn);
            if (value.isEmpty()) {
                continue;
            }
            for (int r = firstRow; r <= Math.min(region.getLastRow(), rows.size() - 1); r++) {
                for (int c = firstColumn; c <= Math.min(region.getLastColumn(), width - 1); c++) {
                    rows.get(r).set(c, value);
                }
            }
        }
    }
}
