package com.fdiet.reference.service;

import com.fdiet.alternative.helpers.FoodCategoriser;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.helpers.DataReader;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.RecommendationPeriod;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto;
import com.fdiet.reference.dto.YieldFactorDto;
import com.fdiet.reference.helpers.ReferenceMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * The shipped reference files read, and say what the documents say.
 *
 * <p>Every figure asserted here was checked against the page of the original
 * document named in its row. This test is what stops a later edit of a CSV from
 * quietly changing one: a reference figure typed wrongly is exactly the wrong
 * number this layer exists to prevent.
 */
class ReferenceDataFilesTest {

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
    void everyRowPointsAtASourceAndAPopulationThatExist() {
        Set<String> sources = rows.sources().stream().map(ReferenceRowsDto.Source::code)
                .collect(Collectors.toSet());
        Set<String> populations = rows.populations().stream().map(ReferenceRowsDto.Population::code)
                .collect(Collectors.toSet());

        assertThat(rows.populations()).allSatisfy(row -> assertThat(sources).contains(row.sourceCode()));
        assertThat(rows.foodMeasures()).allSatisfy(row -> assertThat(sources).contains(row.sourceCode()));
        assertThat(rows.exchangeSystems()).allSatisfy(row -> assertThat(sources).contains(row.sourceCode()));
        assertThat(rows.rations()).allSatisfy(row -> assertThat(populations).contains(row.populationCode()));
        assertThat(rows.recommendations())
                .allSatisfy(row -> assertThat(populations).contains(row.populationCode()));
        assertThat(rows.mealShares()).allSatisfy(row -> assertThat(populations).contains(row.populationCode()));
    }

    @Test
    void loadsOnlySourcesThatMayBeLoadedAsFigures() {
        // Classes A and B are loaded as figures; the diabetes table (C) and the
        // exchange book (D) lend only the definition of their unit, and carry
        // no figures of their own.
        List<String> definitionsOnly = rows.sources().stream()
                .filter(source -> source.licenceClass().ordinal() > 1)
                .map(ReferenceRowsDto.Source::code)
                .toList();
        assertThat(definitionsOnly)
                .containsExactly("FUNDACION-DIABETES-HC", "RUSSOLILLO-MARQUES-2011");
        assertThat(rows.populations()).noneMatch(row -> definitionsOnly.contains(row.sourceCode()));
        assertThat(rows.foodMeasures()).noneMatch(row -> definitionsOnly.contains(row.sourceCode()));
        assertThat(rows.exchangeSystems()).filteredOn(row -> definitionsOnly.contains(row.sourceCode()))
                .allSatisfy(row -> assertThat(row.gramsPerUnit()).isEqualByComparingTo("10"));
    }

    @Test
    void aesan2022AdultRationsAsPublished() {
        assertThat(ration("AESAN-2022:PASTA-ARROZ")).satisfies(row -> {
            assertThat(row.gramsMin()).isEqualByComparingTo("60");
            assertThat(row.gramsMax()).isEqualByComparingTo("80");
            assertThat(row.state()).isEqualTo(FoodState.DRY);
        });
        assertThat(ration("AESAN-2022:LEGUMBRES").gramsMin()).isEqualByComparingTo("50");
        assertThat(ration("AESAN-2022:LEGUMBRES").gramsMax()).isEqualByComparingTo("60");
        assertThat(ration("AESAN-2022:FRUTAS").gramsMin()).isEqualByComparingTo("120");
        assertThat(ration("AESAN-2022:PESCADO").gramsMax()).isEqualByComparingTo("150");
        assertThat(ration("AESAN-2022:HUEVOS").gramsMin()).isEqualByComparingTo("53");
        assertThat(ration("AESAN-2022:LECHE").mlMax()).isEqualByComparingTo("250");
        assertThat(ration("AESAN-2022:ACEITE-OLIVA").mlMin()).isEqualByComparingTo("10");

        ReferenceRowsDto.FoodMeasure spoon = measure("AESAN-2022:M-ACEITE-OLIVA-CS");
        assertThat(spoon.measure()).isEqualTo(HouseholdMeasure.CUCHARADA_SOPERA);
        assertThat(spoon.mlMin()).isEqualByComparingTo("10");
    }

    @Test
    void aesan2022FrequenciesAsPublished() {
        assertThat(recommendation("AESAN-2022:R-LEGUMBRES")).satisfies(row -> {
            assertThat(row.rationsMin()).isEqualByComparingTo("4");
            assertThat(row.period()).isEqualTo(RecommendationPeriod.PER_WEEK);
        });
        assertThat(recommendation("AESAN-2022:R-LACTEOS")).satisfies(row -> {
            assertThat(row.rationsMin()).isNull();
            assertThat(row.rationsMax()).isEqualByComparingTo("3");
            assertThat(row.period()).isEqualTo(RecommendationPeriod.PER_DAY);
        });
        assertThat(recommendation("AESAN-2022:R-CARNE").rationsMax()).isEqualByComparingTo("3");
        assertThat(recommendation("AESAN-2022:R-HUEVOS").rationsMax()).isEqualByComparingTo("4");
    }

    @Test
    void schoolRationsByAgeBandAsPublished() {
        assertThat(ration("AESAN-MEC-2010:3-6:LEGUMBRES-PP").gramsMin()).isEqualByComparingTo("30");
        assertThat(ration("AESAN-MEC-2010:16-18:LEGUMBRES-PP").gramsMin()).isEqualByComparingTo("90");
        assertThat(ration("AESAN-MEC-2010:7-12:ARROZ-PASTA-PP").gramsMax()).isEqualByComparingTo("80");
        assertThat(ration("AESAN-MEC-2010:13-15:POLLO")).satisfies(row -> {
            assertThat(row.gramsMin()).isEqualByComparingTo("230");
            assertThat(row.weightBasis()).isEqualTo(WeightBasis.GROSS);
        });
        assertThat(ration("AESAN-MEC-2010:3-6:FRUTA-FRESCA").gramsMax()).isEqualByComparingTo("100");
        assertThat(ration("AESAN-MEC-2010:16-18:PESCADO-FILETES").gramsMax()).isEqualByComparingTo("160");
        assertThat(rows.rations()).filteredOn(row -> row.code().startsWith("AESAN-MEC-2010:")).hasSize(92);
    }

    @Test
    void schoolMealSharesAddUpToTheWholeDay() {
        BigDecimal low = rows.mealShares().stream().map(ReferenceRowsDto.MealShare::pctMin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal high = rows.mealShares().stream().map(ReferenceRowsDto.MealShare::pctMax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 15 + 0 + 35 + 10 + 30 with a light breakfast and no mid-morning snack
        // is 90; 25 + 10 + 35 + 10 + 30 is 110. The day is 100 in between.
        assertThat(low).isEqualByComparingTo("90");
        assertThat(high).isEqualByComparingTo("110");
    }

    @Test
    void fiveADayPortionsAsPublished() {
        assertThat(ration("5ALDIA-2019:KIWI")).satisfies(row -> {
            assertThat(row.compositionFood())
                    .isEqualTo(new CompositionKey(CompositionSource.CIQUAL, "13021"));
            assertThat(row.gramsMin()).isEqualByComparingTo("80");
            assertThat(row.grossGrams()).isEqualByComparingTo("100");
            assertThat(row.weightBasis()).isEqualTo(WeightBasis.NET_EDIBLE);
        });
        assertThat(ration("5ALDIA-2019:MELON").grossGrams()).isEqualByComparingTo("445");
        assertThat(ration("5ALDIA-2019:ZANAHORIA").gramsMin()).isEqualByComparingTo("100");
        assertThat(ration("5ALDIA-2019:MAIZ-EN-CONSERVA").gramsMin()).isEqualByComparingTo("70");

        ReferenceRowsDto.FoodMeasure apricots = measure("5ALDIA-2019:M-ALBARICOQUE-UNIDAD-MEDIUM");
        assertThat(apricots.count()).isEqualByComparingTo("3");
        assertThat(apricots.gramsMin()).isEqualByComparingTo("180");
        assertThat(apricots.size()).isEqualTo(PortionSize.MEDIUM);
    }

    private ReferenceRowsDto.Ration ration(String code) {
        return rows.rations().stream().filter(row -> row.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("No ration " + code));
    }

    private ReferenceRowsDto.FoodMeasure measure(String code) {
        return rows.foodMeasures().stream().filter(row -> row.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("No measure " + code));
    }

    private ReferenceRowsDto.Recommendation recommendation(String code) {
        return rows.recommendations().stream().filter(row -> row.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("No recommendation " + code));
    }

    @Test
    void usdaCookingYieldsAsPublished() {
        // USDA Table of Cooking Yields for Meat and Poultry, Release 2 (2014), Table 1.
        assertThat(yieldRow("USDA-Y:POLLO-PECHUGA-ASADA").yieldPct()).isEqualByComparingTo("72");
        assertThat(yieldRow("USDA-Y:CERDO-LOMO-PLANCHA").yieldPct()).isEqualByComparingTo("80");
        assertThat(yieldRow("USDA-Y:VACUNO-GUISADO")).satisfies(row -> {
            assertThat(row.yieldPct()).isEqualByComparingTo("67");
            assertThat(row.samples()).isEqualTo(361);
            assertThat(row.foodLabel()).isEqualTo("Beef, retail cuts, all, boneless");
        });
        assertThat(yieldRow("USDA-Y:VACUNO-SOLOMILLO-PLANCHA").foodLabel())
                .isEqualTo("Beef, tenderloin, steak, separable lean and fat, trimmed to 1/8\" fat, all grades");
        Set<String> sources = rows.sources().stream().map(ReferenceRowsDto.Source::code)
                .collect(Collectors.toSet());
        assertThat(rows.yieldFactors()).allSatisfy(row -> {
            assertThat(sources).contains(row.sourceCode());
            assertThat(row.yieldPct()).isBetween(BigDecimal.ONE, new BigDecimal("100"));
        });
    }

    @Test
    void offersTheYieldOfTheCutAndTheMethodTheNameSays() {
        List<YieldFactorDto> yields = rows.yieldFactors().stream()
                .map(row -> new YieldFactorDto(null, row.code(), row.sourceCode(), "USDA 2014",
                        row.foodCategory(), row.keywords(), row.foodLabel(), row.method(),
                        row.methodKeywords(), row.yieldPct(), row.samples(), row.pageRef(), row.note()))
                .toList();
        ReferenceMatcher matcher = new ReferenceMatcher();
        FoodCategoriser categoriser = new FoodCategoriser();

        String thigh = "Pollo, muslo, con piel, asado";
        assertThat(matcher.yieldsCovering(yields, thigh, categoriser.of(thigh), thigh))
                .extracting(YieldFactorDto::code).first().isEqualTo("USDA-Y:POLLO-MUSLO-ASADO");

        // No grilled breast is published: the breast rows are offered, the method not named.
        String breast = "Pollo, pechuga, plancha";
        List<YieldFactorDto> forBreast = matcher.yieldsCovering(yields, breast, categoriser.of(breast), breast);
        assertThat(forBreast).extracting(YieldFactorDto::code)
                .containsExactly("USDA-Y:POLLO-PECHUGA-ASADA", "USDA-Y:POLLO-PECHUGA-HERVIDA");
        assertThat(ReferenceMatcher.namesMethod(forBreast.get(0), breast)).isFalse();

        // The liver is not the chicken.
        String liver = "Higado de pollo, crudo";
        assertThat(matcher.yieldsCovering(yields, liver, categoriser.of(liver), "higado de pollo frito"))
                .extracting(YieldFactorDto::code).containsExactly("USDA-Y:HIGADO-POLLO-FRITO");

        String veal = "Ternera, solomillo, asado";
        assertThat(matcher.yieldsCovering(yields, veal, categoriser.of(veal), veal))
                .extracting(YieldFactorDto::code).first().isEqualTo("USDA-Y:VACUNO-SOLOMILLO-ASADO");
    }

    private ReferenceRowsDto.YieldFactor yieldRow(String code) {
        return rows.yieldFactors().stream().filter(row -> row.code().equals(code)).findFirst()
                .orElseThrow();
    }
}
