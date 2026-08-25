package com.fdiet.food.helpers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesQuotedFieldsWithDelimitersLineBreaksAndDoubledQuotes() throws IOException {
        Path csv = write("""
                ﻿a,b,c
                1,"has, a comma","line
                break"
                2,"a ""quoted"" word",plain
                """);

        List<List<String>> records = new DataReader(csv.toString()).foodData();

        assertThat(records).containsExactly(
                List.of("a", "b", "c"),
                List.of("1", "has, a comma", "line\nbreak"),
                List.of("2", "a \"quoted\" word", "plain"));
    }

    @Test
    void keepsEmptyFieldsAndTheFinalUnterminatedRecord() throws IOException {
        Path csv = write("a,,c\n1,2,3");

        List<List<String>> records = new DataReader(csv.toString()).foodData();

        assertThat(records).containsExactly(List.of("a", "", "c"), List.of("1", "2", "3"));
    }

    private Path write(String content) throws IOException {
        Path csv = tempDir.resolve("food.csv");
        Files.writeString(csv, content, StandardCharsets.UTF_8);
        return csv;
    }
}
