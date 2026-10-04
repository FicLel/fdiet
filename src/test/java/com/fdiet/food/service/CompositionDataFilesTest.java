package com.fdiet.food.service;

import com.fdiet.alternative.helpers.FoodCategoriser;
import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.dto.CompositionStoreResultDto;
import com.fdiet.food.dto.CompositionSyncSummaryDto;
import com.fdiet.food.dto.NutrientDto;
import com.fdiet.food.helpers.BlsTableReader;
import com.fdiet.food.helpers.CiqualTableReader;
import com.fdiet.food.helpers.CompositionLinkReader;
import com.fdiet.food.helpers.DataReader;
import com.fdiet.food.helpers.SheetStreamReader;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.model.Nutrient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The committed CIQUAL 2025 and BLS 4.0 snapshots and fdiet's crosswalk read,
 * through the real readers, and say what the files say. Nothing touches a
 * database: the owning service is a mock that captures what would be stored.
 *
 * <p>Every figure asserted here was read off the upstream file by hand.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositionDataFilesTest {

    private static final Path ROOT = Path.of("reference-data/composition");
    private static final Path NO_ENERGY = ROOT.resolve("composition-es/ciqual_no_energy.csv");
    private static final Path REFUSE = ROOT.resolve("usda-sr-legacy/refuse.csv");
    private static final Path LINKS = ROOT.resolve("composition-es/links.csv");

    private CompositionSyncSummaryDto summary;
    private Map<CompositionKey, CompositionFoodRowDto> rows;

    /** One sync for the whole class: BLS is 14 MB, and reading it once is enough. */
    @BeforeAll
    void sync() {
        ICompositionFoodService store = mock(ICompositionFoodService.class);
        when(store.storeAll(anyList())).thenAnswer(call ->
                new CompositionStoreResultDto(((List<?>) call.getArgument(0)).size(), 0));
        SheetStreamReader sheets = new SheetStreamReader();
        CompositionImportService importService = new CompositionImportService(
                List.of(new CiqualTableReader(sheets), new BlsTableReader(sheets)),
                new CompositionLinkReader(new DataReader("fooddata.csv")),
                store, ROOT.toString());

        summary = importService.sync();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CompositionFoodRowDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).storeAll(captor.capture());
        rows = captor.getValue().stream()
                .collect(Collectors.toMap(CompositionFoodRowDto::key, Function.identity()));
    }

    @Test
    void readsEveryFoodOfBothTables() {
        assertThat(summary.ciqualRows()).isEqualTo(3484);
        assertThat(summary.blsRows()).isEqualTo(7140);
        assertThat(summary.skipped()).isZero();
        assertThat(rows).hasSize(3484 + 7140);
    }

    @Test
    void handsBackBothAttributions() {
        assertThat(summary.attributions())
                .anySatisfy(text -> assertThat(text).contains("Ciqual", "10.5281/zenodo.17550133", "CC BY 4.0"))
                .anySatisfy(text -> assertThat(text).contains("Max Rubner-Institut",
                        "10.25826/Data20251217-134202-0", "CC BY 4.0"));
    }

    /** CIQUAL 20031: "Lettuce, raw", 14,7 kcal, 1,37 g protein — decimal commas read. */
    @Test
    void readsCiqualAsPublished() {
        CompositionFoodRowDto lettuce = rows.get(new CompositionKey(CompositionSource.CIQUAL, "20031"));

        assertThat(lettuce.nameOriginal()).isEqualTo("Laitue, crue");
        assertThat(lettuce.nameEn()).isEqualTo("Lettuce, raw");
        assertThat(lettuce.nutrients().get(Nutrient.ENERGY)).isEqualTo(figure("14.7", "kcal"));
        assertThat(lettuce.nutrients().get(Nutrient.ENERGY).unit()).isEqualTo("kcal");
        assertThat(lettuce.nutrients().get(Nutrient.SODIUM).unit()).isEqualTo("mg");
        assertThat(lettuce.nutrients().get(Nutrient.PROTEIN).unit()).isEqualTo("g");
    }

    /** BLS C131000 "Oat whole grain, raw": 343 kcal, 11.375 g protein, 7.09 g fat, 53.7 g carbohydrate. */
    @Test
    void readsBlsAsPublished() {
        CompositionFoodRowDto oats = rows.get(new CompositionKey(CompositionSource.BLS, "C131000"));

        assertThat(oats.nameOriginal()).isEqualTo("Hafer ganzes Korn, roh");
        assertThat(oats.nameEn()).isEqualTo("Oat whole grain, raw");
        assertThat(oats.foodGroupCode()).isEqualTo("C");
        assertThat(oats.nutrients().get(Nutrient.ENERGY)).isEqualTo(figure("343", "kcal"));
        assertThat(oats.nutrients().get(Nutrient.PROTEIN)).isEqualTo(figure("11.375", "g"));
        assertThat(oats.nutrients().get(Nutrient.FAT)).isEqualTo(figure("7.09", "g"));
        assertThat(oats.nutrients().get(Nutrient.CARBOHYDRATES)).isEqualTo(figure("53.7", "g"));
    }

    /**
     * The tracked list of CIQUAL foods without energy is exactly the foods the
     * workbook leaves without a number, and every one of them is stored with a
     * blank energy rather than an invented one.
     */
    @Test
    void storesNoEnergyForTheTrackedCiqualFoods() {
        Set<String> tracked = new DataReader("fooddata.csv").read(NO_ENERGY).stream().skip(1)
                .map(record -> record.get(0))
                .collect(Collectors.toSet());
        Set<String> blank = rows.values().stream()
                .filter(row -> row.key().source() == CompositionSource.CIQUAL)
                .filter(row -> !row.energyPublished())
                .map(row -> row.key().sourceCode())
                .collect(Collectors.toSet());

        assertThat(tracked).hasSize(145);
        assertThat(blank).isEqualTo(tracked);
        assertThat(summary.withoutEnergy()).isEqualTo(145);
    }

    /**
     * Every one of the fourteen components is found in both files, with the
     * unit it is published in. A header that stopped matching would otherwise
     * blank one component for every food without a word.
     */
    @Test
    void findsEveryComponentInBothTables() {
        Map<Nutrient, String> ciqualUnits = Map.ofEntries(
                Map.entry(Nutrient.ENERGY, "kcal"), Map.entry(Nutrient.PROTEIN, "g"),
                Map.entry(Nutrient.FAT, "g"), Map.entry(Nutrient.SATURATED_FAT, "g"),
                Map.entry(Nutrient.CARBOHYDRATES, "g"), Map.entry(Nutrient.SUGARS, "g"),
                Map.entry(Nutrient.FIBER, "g"), Map.entry(Nutrient.WATER, "g"),
                Map.entry(Nutrient.SODIUM, "mg"), Map.entry(Nutrient.POTASSIUM, "mg"),
                Map.entry(Nutrient.CALCIUM, "mg"), Map.entry(Nutrient.IRON, "mg"),
                Map.entry(Nutrient.CHOLESTEROL, "mg"), Map.entry(Nutrient.VITAMIN_C, "mg"));
        for (CompositionSource source : CompositionSource.values()) {
            Map<Nutrient, Set<String>> units = new HashMap<>();
            rows.values().stream()
                    .filter(row -> row.key().source() == source)
                    .forEach(row -> row.nutrients().forEach((nutrient, figure) ->
                            units.computeIfAbsent(nutrient, n -> new HashSet<>()).add(figure.unit())));

            assertThat(units).as(source.name()).containsOnlyKeys(Nutrient.values());
            ciqualUnits.forEach((nutrient, unit) ->
                    assertThat(units.get(nutrient)).as(source + " " + nutrient).containsExactly(unit));
        }
    }

    @Test
    void linksEveryCrosswalkRowToAFoodThatExists() {
        assertThat(summary.linksUnmatched()).isEmpty();
        assertThat(summary.linked()).isEqualTo(123);
    }

    /**
     * Every edible portion is 1 − refuse/100 of the SR Legacy food the row names,
     * so each one can be traced to the extract it came from.
     */
    @Test
    void takesEveryEdiblePortionFromItsSrLegacyFood() {
        Map<Integer, BigDecimal> refuse = new HashMap<>();
        new DataReader("fooddata.csv").read(REFUSE).stream().skip(1)
                .filter(record -> !record.get(3).isBlank())
                .forEach(record -> refuse.put(Integer.valueOf(record.get(0)), new BigDecimal(record.get(3))));
        assertThat(refuse).hasSize(7751);
        List<CompositionLinkDto> links = new CompositionLinkReader(new DataReader("fooddata.csv")).read(LINKS);

        assertThat(links).filteredOn(link -> link.ediblePortion() != null).hasSize(118)
                .allSatisfy(link -> assertThat(link.ediblePortion()).isEqualByComparingTo(
                        BigDecimal.ONE.subtract(refuse.get(link.ediblePortionFdcId())
                                .movePointLeft(2))));
        assertThat(links).filteredOn(link -> link.ediblePortion() == null)
                .allSatisfy(link -> assertThat(link.ediblePortionFdcId()).isNull());
    }

    /**
     * The Spanish names are written head first ({@code Pollo, pechuga, plancha}),
     * so the categoriser reads them: 121 of the 123. The two it leaves are a herb mix
     * and a dip, which no categoriser rule claims yet.
     */
    @Test
    void writesSpanishNamesTheCategoriserReads() {
        FoodCategoriser categoriser = new FoodCategoriser();
        List<CompositionLinkDto> links = new CompositionLinkReader(new DataReader("fooddata.csv")).read(LINKS);

        assertThat(links).filteredOn(link -> categoriser.of(link.nameEs()) == null)
                .extracting(CompositionLinkDto::nameEs)
                .containsExactlyInAnyOrder("Hierbas provenzales, secas", "Guacamole");
    }

    private static NutrientDto figure(String value, String unit) {
        return new NutrientDto(new BigDecimal(value), unit);
    }
}
