package com.fdiet.diet.service;

import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietImportSummaryDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.dto.SheetGridDto;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.helpers.IDietWorkbookReader;
import com.fdiet.diet.helpers.MealTextParser;
import com.fdiet.diet.model.DietStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The grid below is the shape of example-ui.xlsx: a header row of days, meal
 * rows, and the banner rows a merge spreads across every day column.
 */
class DietImportServiceTest {

    private static final List<List<String>> GRID = List.of(
            List.of("", "Lunes", "Martes"),
            List.of("Desayuno", "Tostada integral (60 gr)", "Tortilla francesa (120 gr)"),
            List.of("Comida", "Comida", "Comida"),
            List.of("Primer plato", "Ensalada: lechuga (80 gr)", "Sopa fría (200 mL)"),
            List.of("Segundo plato", "Pechuga de pollo (180 gr)", "Merluza (180 gr)"),
            List.of("Postre", "1 kiwi", "1 melocotón (180 gr)"),
            List.of("Cena", "Cena", "Cena"),
            List.of("Primer plato", "Crema de calabacín (300 mL)", ""));

    /** Whose diet the import becomes; the reader knows nothing about it. */
    private static final Long PATIENT_ID = 7L;

    private final IDietWorkbookReader reader = mock(IDietWorkbookReader.class);
    private final IDietService dietService = mock(IDietService.class);
    private final DietImportService importService =
            new DietImportService(reader, new MealTextParser(), dietService);

    @Test
    void turnsABannerRowIntoOneMealOfSeveralDishes() {
        DietRequestDto stored = importAndCapture();

        MealDto lunch = mealOf(stored, DayOfWeek.MONDAY, MealType.LUNCH);
        assertThat(lunch.name()).isEqualTo("Comida");
        assertThat(lunch.dishes()).extracting("name")
                .containsExactly("Ensalada", "Segundo plato", "Postre");
        assertThat(lunch.dishes().get(0).ingredients()).extracting("name").containsExactly("lechuga");
    }

    @Test
    void turnsAPlainMealRowIntoOneMealOfOneDish() {
        DietRequestDto stored = importAndCapture();

        MealDto breakfast = mealOf(stored, DayOfWeek.MONDAY, MealType.BREAKFAST);
        assertThat(breakfast.name()).isEqualTo("Desayuno");
        assertThat(breakfast.dishes()).hasSize(1);
        assertThat(breakfast.dishes().get(0).ingredients()).singleElement()
                .satisfies(ingredient -> {
                    assertThat(ingredient).hasFieldOrPropertyWithValue("name", "Tostada integral");
                    assertThat(ingredient).hasFieldOrPropertyWithValue("unit", "gr");
                });
    }

    /** The cell reaches the diet as it was typed, not only as it was read. */
    @Test
    void carriesEachCellAsItWasWritten() {
        DietRequestDto stored = importAndCapture();

        assertThat(mealOf(stored, DayOfWeek.MONDAY, MealType.LUNCH).dishes())
                .extracting("rawText")
                .containsExactly("Ensalada: lechuga (80 gr)", "Pechuga de pollo (180 gr)", "1 kiwi");
    }

    @Test
    void keepsOnlyTheDaysTheHeaderRowNames() {
        DietRequestDto stored = importAndCapture();

        assertThat(stored.days()).extracting(DietDay::day)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
    }

    @Test
    void skipsTheCellsNobodyFilledIn() {
        DietRequestDto stored = importAndCapture();

        // Tuesday's dinner row is empty, so Tuesday has no dinner at all.
        assertThat(mealTypesOf(stored, DayOfWeek.TUESDAY))
                .containsExactly(MealType.BREAKFAST, MealType.LUNCH);
        assertThat(mealTypesOf(stored, DayOfWeek.MONDAY))
                .containsExactly(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER);
    }

    @Test
    void namesTheDietAfterTheSheetWhenNoNameIsGiven() {
        DietRequestDto stored = importAndCapture();

        assertThat(stored.name()).isEqualTo("Dieta 1");
        assertThat(stored.startedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    void storesTheWeekAgainstThePatientItWasImportedFor() {
        // The sheet says nothing about who the diet is for; the caller does.
        assertThat(importAndCapture().patientId()).isEqualTo(PATIENT_ID);
    }

    @Test
    void countsWhatWasStoredAndWhatIsStillUnmatched() {
        when(reader.read(any(), any())).thenReturn(new SheetGridDto("Dieta 1", GRID));
        when(dietService.create(any())).thenAnswer(call -> asStored(call.getArgument(0)));

        DietImportSummaryDto summary = importService.importWorkbook(workbook(), PATIENT_ID, null, null, null, null, null);

        assertThat(summary.sheet()).isEqualTo("Dieta 1");
        assertThat(summary.days()).isEqualTo(2);
        assertThat(summary.meals()).isEqualTo(5);
        assertThat(summary.dishes()).isEqualTo(9);
        // Nothing was matched to the catalogue, and nothing was dropped either.
        assertThat(summary.ingredients()).isEqualTo(9);
        assertThat(summary.unresolved()).isEqualTo(9);
        assertThat(summary.resolved()).isZero();
    }

    @Test
    void refusesASheetWithNoDayNames() {
        when(reader.read(any(), any())).thenReturn(new SheetGridDto("Hoja1",
                List.of(List.of("", "Semana 1"), List.of("Desayuno", "Tostada (60 gr)"))));

        assertThatThrownBy(() -> importService.importWorkbook(workbook(), PATIENT_ID, null, null, null, null, null))
                .isInstanceOf(InvalidDietException.class)
                .hasMessageContaining("no day names");
    }

    /** Runs the import and hands back the week the service was asked to store. */
    private DietRequestDto importAndCapture() {
        when(reader.read(any(), any())).thenReturn(new SheetGridDto("Dieta 1", GRID));
        when(dietService.create(any())).thenAnswer(call -> asStored(call.getArgument(0)));

        importService.importWorkbook(workbook(), PATIENT_ID, null, null, null, null, null);

        ArgumentCaptor<DietRequestDto> captor = ArgumentCaptor.forClass(DietRequestDto.class);
        verify(dietService).create(captor.capture());
        return captor.getValue();
    }

    private static DietDto asStored(DietRequestDto request) {
        return new DietDto(1L, request.patientId(), "Victor", request.name(), DietStatus.ACTIVE,
                request.startedOn(), null, request.referenceProfileCode(), false, request.days(), null);
    }

    private static MealDto mealOf(DietRequestDto diet, DayOfWeek day, MealType type) {
        return diet.days().stream()
                .filter(d -> d.day() == day)
                .flatMap(d -> d.meals().stream())
                .filter(meal -> meal.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " on " + day));
    }

    private static List<MealType> mealTypesOf(DietRequestDto diet, DayOfWeek day) {
        return diet.days().stream()
                .filter(d -> d.day() == day)
                .flatMap(d -> d.meals().stream())
                .map(MealDto::type)
                .toList();
    }

    private static InputStream workbook() {
        return new ByteArrayInputStream(new byte[0]);
    }
}
