package com.fdiet.food.service;

import com.fdiet.food.dto.CategoryDto;
import com.fdiet.food.dto.FoodCsvRowDto;
import com.fdiet.food.dto.FoodItemImportResultDto;
import com.fdiet.food.dto.ImportSummaryDto;
import com.fdiet.food.dto.SubCategoryDto;
import com.fdiet.food.helpers.DataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodImportServiceTest {

    private static final String HEADER = "IdCategoría,Categoría,IdSubcategoría,Subcategoría,Año,Fuente,EAN,"
            + "Nombre comercial,Marca,Denominacion Legal,Lista de ingredientes,Energía kJ,Energía kCal,"
            + "Grasa total,Ácidos grasos saturados,Hidratos de carbono,Azúcares,Proteínas,Sal";

    @TempDir
    Path tempDir;

    private final CategoryService categoryService = mock(CategoryService.class);
    private final SubCategoryService subCategoryService = mock(SubCategoryService.class);
    private final FoodItemService foodItemService = mock(FoodItemService.class);

    @Test
    void skipsTheHeaderAndMapsEveryColumnOfADataRow() throws IOException {
        when(foodItemService.importRows(anyList())).thenReturn(new FoodItemImportResultDto(1, 0));

        ImportSummaryDto summary = importOf(HEADER + "\n"
                + "1,Chocolate,2,Barritas,2022,Kantar,5000159532921,BEKIND BARRA,BEKIND,BARRA DE ALMENDRAS,"
                + "\"ALMENDRAS; MIEL\",2211.0,534.0,40.0,7.9,19.0,14.0,16.0,0.7\n");

        FoodCsvRowDto row = capturedRows().getFirst();
        assertThat(row.categoryId()).isEqualTo(1L);
        assertThat(row.categoryName()).isEqualTo("Chocolate");
        assertThat(row.subCategoryId()).isEqualTo(2L);
        assertThat(row.subCategoryName()).isEqualTo("Barritas");
        assertThat(row.year()).isEqualTo(2022);
        assertThat(row.sourceName()).isEqualTo("Kantar");
        assertThat(row.ean()).isEqualTo("5000159532921");
        assertThat(row.commercialName()).isEqualTo("BEKIND BARRA");
        assertThat(row.brand()).isEqualTo("BEKIND");
        assertThat(row.legalName()).isEqualTo("BARRA DE ALMENDRAS");
        assertThat(row.ingredients()).isEqualTo("ALMENDRAS; MIEL");
        assertThat(row.energyKj()).isEqualByComparingTo("2211.0");
        assertThat(row.energyKcal()).isEqualByComparingTo("534.0");
        assertThat(row.fatG()).isEqualByComparingTo("40.0");
        assertThat(row.saturatedFatG()).isEqualByComparingTo("7.9");
        assertThat(row.carbohydratesG()).isEqualByComparingTo("19.0");
        assertThat(row.sugarsG()).isEqualByComparingTo("14.0");
        assertThat(row.proteinsG()).isEqualByComparingTo("16.0");
        assertThat(row.saltG()).isEqualByComparingTo("0.7");

        assertThat(summary.rowsRead()).isEqualTo(2);
        assertThat(summary.rowsSkipped()).isEqualTo(1);
        assertThat(summary.foodItemsInserted()).isEqualTo(1);
    }

    @Test
    void countsUnusableRowsAsSkippedAndLeavesMissingNumbersNull() throws IOException {
        when(foodItemService.importRows(anyList())).thenReturn(new FoodItemImportResultDto(1, 0));

        ImportSummaryDto summary = importOf(HEADER + "\n"
                // No EAN, so there is nothing to key the item on.
                + "1,Chocolate,2,Barritas,2022,Kantar,,SIN EAN,BEKIND,LEGAL,ING,1,1,1,1,1,1,1,1\n"
                + "1,Chocolate,2,Barritas,2022,Kantar,5000159532921,BEKIND,BEKIND,LEGAL,ING,,,,,,,,\n"
                // Too few columns to read.
                + "1,Chocolate,2\n");

        List<FoodCsvRowDto> rows = capturedRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().energyKj()).isNull();
        assertThat(rows.getFirst().saltG()).isNull();
        assertThat(summary.rowsSkipped()).isEqualTo(3);
    }

    @Test
    void storesEachCategoryAndSubcategoryOnceBeforeTheItems() throws IOException {
        when(foodItemService.importRows(anyList())).thenReturn(new FoodItemImportResultDto(2, 0));

        importOf(HEADER + "\n"
                + "1,Chocolate,2,Barritas,2022,Kantar,111,A,MA,LA,IA,1,1,1,1,1,1,1,1\n"
                + "1,Chocolate,2,Barritas,2022,Kantar,222,B,MB,LB,IB,2,2,2,2,2,2,2,2\n");

        ArgumentCaptor<Collection<CategoryDto>> categories = ArgumentCaptor.forClass(Collection.class);
        verify(categoryService).storeMissing(categories.capture());
        assertThat(categories.getValue()).containsExactly(new CategoryDto(1L, "Chocolate"));

        ArgumentCaptor<Collection<SubCategoryDto>> subCategories = ArgumentCaptor.forClass(Collection.class);
        verify(subCategoryService).storeMissing(subCategories.capture());
        assertThat(subCategories.getValue()).containsExactly(new SubCategoryDto(2L, "Barritas", 1L));
    }

    @Test
    void readsCommaDecimalsAsNumbers() throws IOException {
        when(foodItemService.importRows(anyList())).thenReturn(new FoodItemImportResultDto(1, 0));

        importOf(HEADER + "\n"
                + "1,Chocolate,2,Barritas,2022,Kantar,111,A,MA,LA,IA,\"2211,5\",534,40,7,19,14,16,\"0,7\"\n");

        assertThat(capturedRows().getFirst().energyKj()).isEqualByComparingTo(new BigDecimal("2211.5"));
        assertThat(capturedRows().getFirst().saltG()).isEqualByComparingTo(new BigDecimal("0.7"));
    }

    private ImportSummaryDto importOf(String csv) throws IOException {
        Path file = tempDir.resolve("fooddata.csv");
        Files.writeString(file, csv, StandardCharsets.UTF_8);
        FoodImportService service = new FoodImportService(
                new DataReader(file.toString()), categoryService, subCategoryService, foodItemService);
        return service.importFromCsv();
    }

    @SuppressWarnings("unchecked")
    private List<FoodCsvRowDto> capturedRows() {
        ArgumentCaptor<List<FoodCsvRowDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemService, org.mockito.Mockito.atLeastOnce()).importRows(captor.capture());
        return captor.getValue();
    }
}
