package com.fdiet.food.service;

import com.fdiet.food.dto.BedcaCsvRowDto;
import com.fdiet.food.dto.BedcaStoreResultDto;
import com.fdiet.food.dto.BedcaSyncSummaryDto;
import com.fdiet.food.helpers.DataReader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rows below are the shape of bedca_foods.csv, with the columns in a
 * different order than the real file to prove they are found by name.
 */
class BedcaImportServiceTest {

    private static final List<String> HEADER = List.of(
            "f_id", "f_ori_name", "f_eng_name", "sci_name", "namelevel1", "namelevel2",
            "f_origen", "edible_portion",
            "ENERC", "ENERC_unit", "PROT", "PROT_unit", "CHO", "CHO_unit", "NA", "NA_unit");

    private static final List<String> LECHUGA = List.of(
            "2399", "Lechuga", "Lettuce", "Lactuca sativa",
            "Vegetables and vegetable products", "Leafy vegetables", "BEDCA2", "1.000000",
            "65.125", "kJ", "1.125", "g", "1.4", "g", "3", "mg");

    private final DataReader dataReader = mock(DataReader.class);
    private final IBedcaFoodService bedcaFoodService = mock(IBedcaFoodService.class);
    private final BedcaImportService importService =
            new BedcaImportService(dataReader, bedcaFoodService, "bedca_foods.csv");

    @Test
    void readsTheColumnsByNameAndKeepsTheFiguresVerbatim() {
        BedcaCsvRowDto row = importAndCapture(LECHUGA).get(0);

        assertThat(row.id()).isEqualTo(2399L);
        assertThat(row.name()).isEqualTo("Lechuga");
        assertThat(row.englishName()).isEqualTo("Lettuce");
        assertThat(row.foodGroup()).isEqualTo("Vegetables and vegetable products");
        assertThat(row.origin()).isEqualTo("BEDCA2");
        assertThat(row.ediblePortion()).isEqualByComparingTo("1.000000");

        // 65.125 kJ, not 15.6 kcal: nothing is converted on the way in.
        assertThat(row.nutrients().get("energy").value()).isEqualByComparingTo("65.125");
        assertThat(row.nutrients().get("energy").unit()).isEqualTo("kJ");
        assertThat(row.nutrients().get("sodium").unit()).isEqualTo("mg");
    }

    @Test
    void leavesOutAComponentTheSourcePublishedNothingFor() {
        List<String> noProtein = List.of(
                "2400", "Ejemplo", "", "", "", "", "BEDCA", "1.000000",
                "100", "kJ", "", "", "2", "g", "1", "mg");

        BedcaCsvRowDto row = importAndCapture(noProtein).get(0);

        // Absent, not zero: unmeasured and none are different answers.
        assertThat(row.nutrients()).doesNotContainKey("protein");
        assertThat(row.nutrients()).containsKeys("energy", "carbohydrates", "sodium");
    }

    @Test
    void skipsARowWithNothingToKeyItOn() {
        List<String> nameless = List.of(
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");

        when(dataReader.read(any(Path.class))).thenReturn(List.of(HEADER, LECHUGA, nameless));
        when(bedcaFoodService.storeAll(any())).thenReturn(new BedcaStoreResultDto(1, 0));

        BedcaSyncSummaryDto summary = importService.sync();

        assertThat(summary.rows()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.inserted()).isEqualTo(1);
    }

    @Test
    void handsBackTheAttributionTheFiguresMayNotBeShownWithout() {
        when(dataReader.read(any(Path.class))).thenReturn(List.of(HEADER, LECHUGA));
        when(bedcaFoodService.storeAll(any())).thenReturn(new BedcaStoreResultDto(0, 1));

        assertThat(importService.sync().source()).isEqualTo(IBedcaImportService.SOURCE);
        assertThat(IBedcaImportService.SOURCE).contains("AESAN/BEDCA");
    }

    @Test
    void doesNothingWithAnEmptyFile() {
        when(dataReader.read(any(Path.class))).thenReturn(List.of());

        BedcaSyncSummaryDto summary = importService.sync();

        assertThat(summary.rows()).isZero();
        assertThat(summary.inserted()).isZero();
    }

    /** Runs the sync and hands back the rows the service was asked to store. */
    private List<BedcaCsvRowDto> importAndCapture(List<String> record) {
        when(dataReader.read(any(Path.class))).thenReturn(List.of(HEADER, record));
        when(bedcaFoodService.storeAll(any())).thenReturn(new BedcaStoreResultDto(1, 0));

        importService.sync();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BedcaCsvRowDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(bedcaFoodService).storeAll(captor.capture());
        return captor.getValue();
    }
}
