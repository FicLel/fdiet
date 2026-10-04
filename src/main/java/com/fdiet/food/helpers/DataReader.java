package com.fdiet.food.helpers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the Spanish food dataset as raw rows of columns.
 *
 * <p>Parses RFC 4180 CSV: fields may be quoted, and a quoted field may contain
 * the delimiter, line breaks, and doubled quotes ({@code ""}) as an escaped quote.
 */
@Component
public class DataReader {
    public static final char DELIMITER = ',';
    private static final char QUOTE = '"';
    private static final char BOM = '\uFEFF';
    private static final int BUFFER_SIZE = 8192;

    private final Path file;

    public DataReader(@Value("${fdiet.food.csv-path:fooddata.csv}") String csvPath) {
        this.file = Path.of(csvPath);
    }

    public List<List<String>> foodData() {
        return read(file);
    }

    /**
     * Any CSV of the same dialect — the composition crosswalk and the reference
     * CSVs are read through it, so the parser is shared rather than written twice.
     */
    public List<List<String>> read(Path csv) {
        try (Reader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read food data from " + csv.toAbsolutePath(), e);
        }
    }

    private List<List<String>> parse(Reader reader) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        boolean inQuotes = false;
        boolean quoteSeen = false;
        boolean atStartOfFile = true;

        char[] buffer = new char[BUFFER_SIZE];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            for (int i = 0; i < count; i++) {
                char c = buffer[i];

                if (atStartOfFile) {
                    atStartOfFile = false;
                    if (c == BOM) {
                        continue;
                    }
                }

                if (inQuotes) {
                    if (quoteSeen) {
                        quoteSeen = false;
                        if (c == QUOTE) {
                            field.append(QUOTE);
                            continue;
                        }
                        // The quote closed the field; fall through and treat c as unquoted.
                        inQuotes = false;
                    } else if (c == QUOTE) {
                        quoteSeen = true;
                        continue;
                    } else {
                        // Delimiters and line breaks are data inside a quoted field.
                        field.append(c);
                        continue;
                    }
                }

                switch (c) {
                    case QUOTE -> inQuotes = true;
                    case DELIMITER -> {
                        record.add(field.toString());
                        field.setLength(0);
                    }
                    case '\r' -> {
                        // Ignored: CRLF ends the record on the '\n'.
                    }
                    case '\n' -> {
                        if (!record.isEmpty() || !field.isEmpty()) {
                            record.add(field.toString());
                            records.add(record);
                            record = new ArrayList<>();
                        }
                        field.setLength(0);
                    }
                    default -> field.append(c);
                }
            }
        }

        // A final record not terminated by a line break.
        if (!record.isEmpty() || !field.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
