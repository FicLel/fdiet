package com.fdiet.reference.service;

import com.fdiet.food.helpers.DataReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The licence audit of FD-033 phase E, kept true by the build: every source in
 * {@code reference-data/sources.csv} names its licence in the {@code spdx}
 * column and allows commercial use, and every file of
 * {@code reference-data/composition/manifest.csv} carries an open SPDX id. An
 * SPDX id where one exists; the AESAN reuse notice and US public domain have
 * none and are named plainly.
 *
 * <p>The importers ignore these columns, so this is the one place they are
 * checked. A gated, NC or ND source added later fails here, before it is loaded.
 */
class ReferenceLicenceAuditTest {

    private static final Path SOURCES = Path.of("reference-data/sources.csv");
    private static final Path COMPOSITION_MANIFEST = Path.of("reference-data/composition/manifest.csv");
    private static final String CODE = "code";
    private static final String FILE = "file";
    private static final String SPDX = "spdx";
    private static final String COMMERCIAL_USE = "commercial_use";

    /** The licences fdiet accepts as open for commercial reuse (decision of 2026-10-02). */
    private static final Set<String> OPEN_LICENCES = Set.of(
            "CC-BY-4.0", "CC-BY-SA-4.0", "CC0-1.0",
            "AESAN reuse notice (aviso legal; Ley 37/2007)",
            "US public domain (17 U.S.C. § 105)");

    @Test
    void everySourceNamesAnOpenLicenceAndAllowsCommercialUse() {
        List<Map<String, String>> sources = rows(SOURCES);

        assertThat(sources).isNotEmpty().allSatisfy(source -> {
            assertThat(source.get(SPDX)).as(source.get(CODE)).isIn(OPEN_LICENCES);
            assertThat(source.get(COMMERCIAL_USE)).as(source.get(CODE)).isEqualTo("true");
        });
    }

    /** The exchange definitions are fdiet's method-only work, published under CC BY 4.0. */
    @Test
    void publishesTheMethodOnlyDefinitionsUnderCcBy() {
        Map<String, String> licenceByCode = rows(SOURCES).stream()
                .collect(Collectors.toMap(row -> row.get(CODE), row -> row.get(SPDX)));

        assertThat(licenceByCode)
                .containsEntry("FUNDACION-DIABETES-HC", "CC-BY-4.0")
                .containsEntry("RUSSOLILLO-MARQUES-2011", "CC-BY-4.0");
    }

    /** CIQUAL, BLS, the SR Legacy extract and fdiet's crosswalk: every file says its licence. */
    @Test
    void everyCompositionFileCarriesAnOpenSpdxId() {
        List<Map<String, String>> files = rows(COMPOSITION_MANIFEST);

        assertThat(files).isNotEmpty().allSatisfy(file ->
                assertThat(file.get(SPDX)).as(file.get(FILE)).isIn(OPEN_LICENCES));
    }

    private static List<Map<String, String>> rows(Path csv) {
        List<List<String>> records = new DataReader(csv.toString()).read(csv);
        List<String> header = records.getFirst();
        return records.subList(1, records.size()).stream()
                .map(record -> IntStream.range(0, header.size()).boxed()
                        .collect(Collectors.toMap(header::get, record::get)))
                .toList();
    }
}
