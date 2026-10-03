package com.fdiet.food.helpers;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads the first sheet of an .xlsx one row at a time, the way
 * {@link DataReader} hands back a CSV: every cell as text, nothing interpreted.
 *
 * <p>Streaming rather than loading the workbook: BLS 4.0 is 7,140 rows by 418
 * columns, which the in-memory model would need a gigabyte or more to hold. Here
 * one row is in memory at a time.
 */
public interface ISheetStreamReader {

    /**
     * Hands every row of the first sheet to {@code row}, header first, in order.
     * A row is as wide as its last filled cell; a missing cell reads as an empty
     * string. Numbers come back unformatted ({@code 11.45}, never a rounded
     * display value).
     */
    void forEachRow(Path workbook, Consumer<List<String>> row);
}
