package com.fdiet.reference.service;

import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.helpers.CompositionLinkReader;
import com.fdiet.food.helpers.DataReader;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto;
import com.fdiet.reference.exception.InvalidReferenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The composition foods the reference CSVs name (FD-033 phase C): every one is a
 * row of fdiet's crosswalk, so it has a Spanish name a person wrote, and the
 * V16 migration re-keyed the stored rows to the same foods the CSVs now name.
 */
class ReferenceCompositionKeysTest {

    private static final Path LINKS = Path.of("reference-data/composition/composition-es/links.csv");
    private static final Path V16 =
            Path.of("src/main/resources/db/migration/V16__rekey_reference_foods_to_composition.sql");
    private static final Pattern MAPPING_ROW = Pattern.compile("\\((\\d+), '(CIQUAL|BLS)', '([A-Z0-9]+)'\\)");

    private final IReferenceService referenceService = mock(IReferenceService.class);
    private ReferenceRowsDto rows;

    @BeforeEach
    void read() {
        when(referenceService.store(any())).thenReturn(
                new ReferenceSyncSummaryDto(List.of(), List.of(), List.of()));
        new ReferenceImportService(new DataReader("fooddata.csv"), referenceService, "reference-data")
                .sync();
        ArgumentCaptor<ReferenceRowsDto> captor = ArgumentCaptor.forClass(ReferenceRowsDto.class);
        verify(referenceService).store(captor.capture());
        rows = captor.getValue();
    }

    @Test
    void keysEveryFiveADayFoodItNamedBefore() {
        assertThat(rows.rations()).filteredOn(row -> row.compositionFood() != null).hasSize(60)
                .allSatisfy(row -> assertThat(row.code()).startsWith("5ALDIA-2019:"));
        assertThat(rows.foodMeasures()).filteredOn(row -> row.compositionFood() != null).hasSize(62)
                .allSatisfy(row -> assertThat(row.code()).startsWith("5ALDIA-2019:"));
        assertThat(usedKeys()).hasSize(60);
    }

    /**
     * A food without a crosswalk row would carry no Spanish name: nothing could read its
     * family. A row nobody approved is a guess, so every one the CSVs name is reviewed.
     */
    @Test
    void namesOnlyFoodsTheCrosswalkNames() {
        Map<CompositionKey, CompositionLinkDto> links = links();

        assertThat(links.keySet()).containsAll(usedKeys());
        assertThat(usedKeys()).filteredOn(key -> !links.get(key).reviewed()).isEmpty();
    }

    /** The migration re-keyed the stored rows to the foods the CSVs name, every approved pick included. */
    @Test
    void theMigrationMapsToTheSameFoodsAsTheFiles() throws IOException {
        String sql = Files.readString(V16, StandardCharsets.UTF_8);
        Set<CompositionKey> mapped = new HashSet<>();
        Set<Long> bedcaIds = new HashSet<>();
        Matcher row = MAPPING_ROW.matcher(sql);
        while (row.find()) {
            assertThat(bedcaIds.add(Long.valueOf(row.group(1)))).as("BEDCA " + row.group(1)).isTrue();
            assertThat(mapped.add(new CompositionKey(CompositionSource.valueOf(row.group(2)), row.group(3))))
                    .as("one composition food per BEDCA food").isTrue();
        }

        assertThat(mapped).isEqualTo(usedKeys());
    }

    /** A row naming its table without the code, or the code without the table, is a mistake in the file. */
    @Test
    void refusesAHalfWrittenCompositionKey(@TempDir Path root) throws IOException {
        Path folder = Files.createDirectory(root.resolve("test-source"));
        Files.writeString(folder.resolve("food_measures.csv"), String.join("\n",
                "code,source_code,measure,size,count,composition_source,composition_code,food_category,"
                        + "keywords,food_label,grams_min,grams_max,ml_min,ml_max,state,weight_basis,"
                        + "gross_grams,household_text,page_ref,note",
                "T:M-KIWI,T,UNIDAD,,1,CIQUAL,,FRUIT,,Kiwi,80,80,,,UNSPECIFIED,NET_EDIBLE,,1 kiwi,p. 1,"));
        ReferenceImportService importer = new ReferenceImportService(new DataReader("fooddata.csv"),
                mock(IReferenceService.class), root.toString());

        assertThatThrownBy(importer::sync)
                .isInstanceOf(InvalidReferenceException.class)
                .hasMessageContaining("food_measures.csv:2")
                .hasMessageContaining("composition_source and composition_code go together");
    }

    private Set<CompositionKey> usedKeys() {
        return Stream.concat(rows.rations().stream().map(ReferenceRowsDto.Ration::compositionFood),
                        rows.foodMeasures().stream().map(ReferenceRowsDto.FoodMeasure::compositionFood))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Map<CompositionKey, CompositionLinkDto> links() {
        return new CompositionLinkReader(new DataReader("fooddata.csv")).read(LINKS).stream()
                .collect(Collectors.toMap(CompositionLinkDto::key, Function.identity()));
    }
}
