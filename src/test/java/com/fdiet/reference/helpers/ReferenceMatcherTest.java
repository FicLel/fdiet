package com.fdiet.reference.helpers;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.RationRole;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.dto.RationDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A household measure is attached on its own only when attaching it is not a
 * judgement, and a ration is chosen for a food only when choosing is not a guess.
 */
class ReferenceMatcherTest {

    /** Composition food ids, as reference rows and criteria name them. */
    private static final long KIWI = 2228L;
    private static final long AOVE = 2544L;
    private static final long EGG = 2127L;
    private static final String AESAN = "AESAN-2022-007";
    private static final String SENC = "SENC-2018";

    private final ReferenceMatcher matcher = new ReferenceMatcher();

    @Test
    void attachesTheOnlyRowThereIs() {
        FoodMeasureDto kiwi = measure(1L, "5ALDIA-2019", HouseholdMeasure.UNIDAD, PortionSize.MEDIUM,
                KIWI, null, null, "80", "80");

        MeasureChoiceDto choice = matcher.chooseMeasure(List.of(kiwi), List.of(), List.of(),
                query(KIWI, "Kiwi", "unidad", null, null), FoodCategory.FRUIT, AESAN);

        assertThat(choice.chosen()).isEqualTo(kiwi);
    }

    /**
     * A BEDCA-matched ingredient (until FD-033 phase D) is known by its name only:
     * a row naming a composition food never covers it, whatever its family.
     */
    @Test
    void aRowNamingAFoodNeverCoversAFoodKnownByNameOnly() {
        FoodMeasureDto kiwi = measure(1L, "5ALDIA-2019", HouseholdMeasure.UNIDAD, PortionSize.MEDIUM,
                KIWI, FoodCategory.FRUIT, null, "80", "80");

        MeasureChoiceDto choice = matcher.chooseMeasure(List.of(kiwi), List.of(), List.of(),
                MeasureQueryDto.byNameOnly("Kiwi", "unidad", null, null), FoodCategory.FRUIT, AESAN);

        assertThat(choice.chosen()).isNull();
        assertThat(choice.candidates()).isEmpty();
    }

    /** No Spanish name yet, so no family: the row naming the food still weighs it. */
    @Test
    void aCompositionFoodWithoutANameIsWeighedByTheRowNamingIt() {
        FoodMeasureDto kiwi = measure(1L, "5ALDIA-2019", HouseholdMeasure.UNIDAD, PortionSize.MEDIUM,
                KIWI, null, null, "80", "80");

        MeasureChoiceDto choice = matcher.chooseMeasure(List.of(kiwi), List.of(), List.of(),
                query(KIWI, null, "unidad", null, null), null, AESAN);

        assertThat(choice.chosen()).isEqualTo(kiwi);
    }

    @Test
    void aQueryNamingNoFoodIsWeighedByNothing() {
        FoodMeasureDto kiwi = measure(1L, "5ALDIA-2019", HouseholdMeasure.UNIDAD, PortionSize.MEDIUM,
                KIWI, null, null, "80", "80");

        assertThat(matcher.chooseMeasure(List.of(kiwi), List.of(), List.of(),
                query(null, null, "unidad", null, null), null, AESAN)).isEqualTo(MeasureChoiceDto.NONE);
    }

    @Test
    void theProfileSourceSettlesADisagreementBetweenSources() {
        FoodMeasureDto aesan = oil(1L, AESAN, "10");
        FoodMeasureDto senc = oil(2L, SENC, "15");

        MeasureChoiceDto choice = matcher.chooseMeasure(List.of(aesan, senc), List.of(), List.of(),
                query(AOVE, "Aceite de oliva virgen extra", "cda", null, null), FoodCategory.FAT_OIL, AESAN);

        assertThat(choice.chosen()).isEqualTo(aesan);
        assertThat(choice.candidates()).containsExactly(aesan, senc);
    }

    @Test
    void sourcesThatDisagreeWithNoProfileToDecideAreOfferedNotAttached() {
        FoodMeasureDto aesan = oil(1L, AESAN, "10");
        FoodMeasureDto senc = oil(2L, SENC, "15");

        MeasureChoiceDto choice = matcher.chooseMeasure(List.of(aesan, senc), List.of(), List.of(),
                query(AOVE, "Aceite de oliva virgen extra", "cucharada sopera", null, null),
                FoodCategory.FAT_OIL, null);

        assertThat(choice.chosen()).isNull();
        assertThat(choice.candidates()).hasSize(2);
    }

    @Test
    void aRangeWeighsNothingAndIsNeverAttachedOnItsOwn() {
        FoodMeasureDto egg = measure(1L, AESAN, HouseholdMeasure.UNIDAD, PortionSize.MEDIUM, null,
                FoodCategory.EGG, "huevo", "53", "63");

        MeasureChoiceDto choice = matcher.chooseMeasure(List.of(egg), List.of(), List.of(),
                query(2127L, "Huevo de gallina fresco", "unidad", null, null), FoodCategory.EGG, AESAN);

        assertThat(choice.chosen()).isNull();
        assertThat(choice.candidates()).containsExactly(egg);
    }

    @Test
    void theDietsOwnCriterionComesBeforeAnyPublishedRow() {
        FoodMeasureDto aesan = oil(1L, AESAN, "10");
        FoodMeasureDto own = new FoodMeasureDto(9L, null, HouseholdMeasure.CUCHARADA_SOPERA,
                "cucharada sopera", null, BigDecimal.ONE, "Aceite de oliva virgen extra", AOVE,
                FoodCategory.FAT_OIL, null, null, null, new BigDecimal("12"), new BigDecimal("12"),
                new BigDecimal("12"), FoodState.UNSPECIFIED, WeightBasis.NET_EDIBLE, null, null, null,
                null, null, null, null, 5L, true, false);

        MeasureChoiceDto choice = matcher.chooseMeasure(List.of(aesan), List.of(own), List.of(),
                query(AOVE, "Aceite de oliva virgen extra", "cda", null, null), FoodCategory.FAT_OIL, AESAN);

        assertThat(choice.chosen()).isEqualTo(own);
        assertThat(choice.candidates()).containsExactly(own, aesan);
    }

    /** A published range weighs nothing; the nutritionist's global criterion settles it for every diet. */
    @Test
    void theGlobalCriterionWeighsWhatAPublishedRangeCannot() {
        FoodMeasureDto range = measure(1L, AESAN, HouseholdMeasure.UNIDAD, PortionSize.MEDIUM, null,
                FoodCategory.EGG, "huevo", "53", "63");
        FoodMeasureDto global = criterion(20L, null, EGG, HouseholdMeasure.UNIDAD, PortionSize.MEDIUM, "58");

        MeasureChoiceDto choice = matcher.chooseMeasure(List.of(range), List.of(), List.of(global),
                query(EGG, "Huevo, entero, crudo", "unidades", PortionSize.MEDIUM, null),
                FoodCategory.EGG, AESAN);

        assertThat(choice.chosen()).isEqualTo(global);
        assertThat(choice.candidates()).containsExactly(global, range);
    }

    /** Precedence: picked, then the diet's criterion, then the global one, then published. */
    @Test
    void theDietsCriterionWinsOverTheGlobalOneAndAPickWinsOverBoth() {
        FoodMeasureDto published = oil(1L, AESAN, "10");
        FoodMeasureDto global = criterion(20L, null, AOVE, HouseholdMeasure.CUCHARADA_SOPERA, null, "11");
        FoodMeasureDto own = criterion(30L, 5L, AOVE, HouseholdMeasure.CUCHARADA_SOPERA, null, "12");
        MeasureQueryDto spoon = query(AOVE, "Aceite de oliva virgen extra", "cda", null, null);

        assertThat(matcher.chooseMeasure(List.of(published), List.of(own), List.of(global), spoon,
                FoodCategory.FAT_OIL, AESAN).chosen()).isEqualTo(own);
        assertThat(matcher.chooseMeasure(List.of(published), List.of(), List.of(global), spoon,
                FoodCategory.FAT_OIL, AESAN).chosen()).isEqualTo(global);
        assertThat(matcher.chooseMeasure(List.of(published), List.of(own), List.of(global),
                query(AOVE, "Aceite de oliva virgen extra", "cda", null, 1L),
                FoodCategory.FAT_OIL, AESAN).chosen()).isEqualTo(published);
    }

    @Test
    void keepsTheMeasureAPersonPickedEvenWhenTheRuleWouldNot() {
        FoodMeasureDto aesan = oil(1L, AESAN, "10");
        FoodMeasureDto senc = oil(2L, SENC, "15");

        MeasureChoiceDto choice = matcher.chooseMeasure(List.of(aesan, senc), List.of(), List.of(),
                query(AOVE, "Aceite de oliva virgen extra", "cda", null, 2L), FoodCategory.FAT_OIL, AESAN);

        assertThat(choice.chosen()).isEqualTo(senc);
    }

    @Test
    void aSizeTheTextDidNotSayCannotChooseBetweenSizesThatWeighDifferently() {
        FoodMeasureDto small = measure(1L, "5ALDIA-2019", HouseholdMeasure.UNIDAD, PortionSize.SMALL,
                2229L, null, null, "120", "120", "2");
        FoodMeasureDto large = measure(2L, "5ALDIA-2019", HouseholdMeasure.UNIDAD, PortionSize.LARGE,
                2229L, null, null, "120", "120", "1");

        assertThat(matcher.chooseMeasure(List.of(small, large), List.of(), List.of(),
                query(2229L, "Mandarina", "unidad", null, null), FoodCategory.FRUIT, AESAN).chosen()).isNull();
        assertThat(matcher.chooseMeasure(List.of(small, large), List.of(), List.of(),
                query(2229L, "Mandarina", "unidad", PortionSize.LARGE, null), FoodCategory.FRUIT, AESAN)
                .chosen()).isEqualTo(large);
    }

    @Test
    void aRawMeasureDoesNotWeighACookedFood() {
        FoodMeasureDto legumes = new FoodMeasureDto(1L, "X", HouseholdMeasure.CUCHARADA_SOPERA,
                "cucharada sopera", null, BigDecimal.ONE, "Legumbres en crudo", null, FoodCategory.LEGUME,
                null, new BigDecimal("15"), new BigDecimal("15"), null, null, new BigDecimal("15"),
                FoodState.RAW, WeightBasis.NET_EDIBLE, null, null, null, null, "AESAN-MEC-2010",
                "AESAN/MEC 2010", 1, null, false, false);

        assertThat(matcher.chooseMeasure(List.of(legumes), List.of(), List.of(),
                query(2200L, "Lenteja, hervida", "cda", null, null), FoodCategory.LEGUME, null)
                .candidates()).isEmpty();
        assertThat(matcher.chooseMeasure(List.of(legumes), List.of(), List.of(),
                query(1065L, "Lenteja, seca, cruda", "cda", null, null), FoodCategory.LEGUME, null)
                .chosen()).isEqualTo(legumes);
    }

    @Test
    void countsAFoodInTheMostSpecificRationOfItsFamily() {
        RationDto milk = ration("LECHE", FoodCategory.DAIRY, "leche;!fermentada", null);
        RationDto cheese = ration("QUESO-CURADO", FoodCategory.DAIRY, "queso", null);
        RationDto freshCheese = ration("QUESO-FRESCO", FoodCategory.DAIRY, "queso fresco;requeson", null);
        List<RationDto> rations = List.of(milk, cheese, freshCheese);

        assertThat(matcher.countingRation(rations, 2507L, "Queso fresco de burgos", FoodCategory.DAIRY))
                .isEqualTo(freshCheese);
        assertThat(matcher.countingRation(rations, 719L, "Queso Zamorano", FoodCategory.DAIRY))
                .isEqualTo(cheese);
        assertThat(matcher.countingRation(rations, 2518L, "Leche fermentada, bifidobacterium",
                FoodCategory.DAIRY)).isNull();
    }

    @Test
    void aMainCourseRationIsTheRationWhenAGuidelineSizesByRole() {
        RationDto main = ration("LEGUMBRES-PP", FoodCategory.LEGUME, null, RationRole.PLATO_PRINCIPAL);
        RationDto side = ration("LEGUMBRES-GUARNICION", FoodCategory.LEGUME, null, RationRole.GUARNICION);

        assertThat(matcher.countingRation(List.of(main, side), 1065L, "Lenteja, seca, cruda",
                FoodCategory.LEGUME)).isEqualTo(main);
    }

    private static FoodMeasureDto oil(Long id, String source, String ml) {
        return new FoodMeasureDto(id, "M" + id, HouseholdMeasure.CUCHARADA_SOPERA, "cucharada sopera",
                null, BigDecimal.ONE, "Aceite de oliva", null, FoodCategory.FAT_OIL, "aceite de oliva",
                null, null, new BigDecimal(ml), new BigDecimal(ml), new BigDecimal(ml),
                FoodState.UNSPECIFIED, WeightBasis.UNSPECIFIED, null, "1 cucharada sopera", "p. 1", null,
                source, source, 1, null, false, false);
    }

    /** A nutritionist's row: one diet's when {@code dietId} is set, her global one otherwise. */
    private static FoodMeasureDto criterion(Long id, Long dietId, Long foodId, HouseholdMeasure measure,
                                            PortionSize size, String grams) {
        BigDecimal weight = new BigDecimal(grams);
        return new FoodMeasureDto(id, null, measure, measure.label(), size, BigDecimal.ONE, "food",
                foodId, null, null, weight, weight, null, null, weight, FoodState.UNSPECIFIED,
                WeightBasis.NET_EDIBLE, null, null, null, null, null, null, null, dietId,
                dietId != null, dietId == null);
    }

    private static FoodMeasureDto measure(Long id, String source, HouseholdMeasure measure,
                                          PortionSize size, Long foodId, FoodCategory category,
                                          String keywords, String min, String max) {
        return measure(id, source, measure, size, foodId, category, keywords, min, max, "1");
    }

    private static FoodMeasureDto measure(Long id, String source, HouseholdMeasure measure,
                                          PortionSize size, Long foodId, FoodCategory category,
                                          String keywords, String min, String max, String count) {
        BigDecimal perMeasure = min.equals(max)
                ? new BigDecimal(min).divide(new BigDecimal(count), 4, java.math.RoundingMode.HALF_UP)
                : null;
        return new FoodMeasureDto(id, "M" + id, measure, measure.label(), size, new BigDecimal(count),
                "food", foodId, category, keywords, new BigDecimal(min), new BigDecimal(max), null, null,
                perMeasure, FoodState.UNSPECIFIED, WeightBasis.NET_EDIBLE, null, null, "p. 1", null,
                source, source, 1, null, false, false);
    }

    private static MeasureQueryDto query(Long foodId, String name, String unit, PortionSize size,
                                         Long preferred) {
        return new MeasureQueryDto(foodId, name, unit, size, preferred);
    }

    private static RationDto ration(String code, FoodCategory category, String keywords, RationRole role) {
        return new RationDto(1L, code, "P", "P", "S", "S", code, code, category, keywords, null, null,
                role, new BigDecimal("50"), new BigDecimal("60"), null, null, null, null,
                FoodState.UNSPECIFIED, WeightBasis.UNSPECIFIED, null, null, "p. 1", null);
    }
}
