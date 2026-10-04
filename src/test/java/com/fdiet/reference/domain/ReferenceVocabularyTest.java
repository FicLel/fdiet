package com.fdiet.reference.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The words the reference layer reads: states, sizes, household measures and keywords. */
class ReferenceVocabularyTest {

    @Test
    void readsTheStateABedcaNameStatesAndNothingMore() {
        assertThat(FoodState.ofFoodName("Lenteja, hervida")).isEqualTo(FoodState.COOKED);
        assertThat(FoodState.ofFoodName("Lenteja, seca, cruda")).isEqualTo(FoodState.DRY);
        assertThat(FoodState.ofFoodName("Patata, cruda")).isEqualTo(FoodState.RAW);
        assertThat(FoodState.ofFoodName("Garbanzo, en conserva")).isEqualTo(FoodState.CANNED);
        assertThat(FoodState.ofFoodName("Soja, seca, remojada, hervida")).isEqualTo(FoodState.COOKED);
        assertThat(FoodState.ofFoodName("Pollo, pechuga, plancha")).isEqualTo(FoodState.COOKED);
        assertThat(FoodState.ofFoodName("Lechuga")).isEqualTo(FoodState.UNSPECIFIED);
        assertThat(FoodState.ofFoodName("Arroz")).isEqualTo(FoodState.UNSPECIFIED);
    }

    /** FD-060: "cocinado" is the word the composer writes for COOKED, and a person writes it too. */
    @Test
    void readsCocinadoInEveryGenderAndNumberAsCooked() {
        for (String word : List.of("cocinado", "cocinada", "cocinados", "cocinadas")) {
            assertThat(FoodState.ofWriting("lentejas", "150 g " + word)).as(word).isEqualTo(FoodState.COOKED);
            assertThat(FoodState.ofWriting("lentejas " + word, "")).as(word).isEqualTo(FoodState.COOKED);
            assertThat(FoodState.ofFoodName("Lenteja, " + word)).as(word).isEqualTo(FoodState.COOKED);
        }
        // The bracket still wins over a name on the other side of cooking (FD-052).
        assertThat(FoodState.ofWriting("Lenteja, seca, cruda", "150 g cocinado")).isEqualTo(FoodState.COOKED);
        assertThat(FoodState.ofWriting("arroz cocinado", "70 g en crudo")).isEqualTo(FoodState.RAW);
        // A word that only starts the same way is not the state.
        assertThat(FoodState.ofFoodName("Cocinilla")).isEqualTo(FoodState.UNSPECIFIED);
    }

    /** Every word fdiet writes for a state reads back as that state, alone and against a name. */
    @Test
    void everyWrittenStateReadsBackAsItself() {
        for (FoodState state : FoodState.values()) {
            if (state.written() == null) {
                assertThat(state).isEqualTo(FoodState.UNSPECIFIED);
                continue;
            }
            assertThat(FoodState.ofWriting("lentejas", "150 g " + state.written())).as(state.name()).isEqualTo(state);
        }
    }

    @Test
    void onlyRawAgainstCookedIsADisagreement() {
        assertThat(FoodState.disagree(FoodState.RAW, FoodState.COOKED)).isTrue();
        assertThat(FoodState.disagree(FoodState.DRY, FoodState.CANNED)).isTrue();
        assertThat(FoodState.disagree(FoodState.RAW, FoodState.DRY)).isFalse();
        assertThat(FoodState.disagree(FoodState.RAW, FoodState.UNSPECIFIED)).isFalse();
        assertThat(FoodState.disagree(null, FoodState.COOKED)).isFalse();
    }

    @Test
    void readsOneSizeOrNone() {
        assertThat(PortionSize.ofWriting("1 pera pequeña")).isEqualTo(PortionSize.SMALL);
        assertThat(PortionSize.ofWriting("1 Ud. mediana")).isEqualTo(PortionSize.MEDIUM);
        assertThat(PortionSize.ofWriting("2 patatas grandes")).isEqualTo(PortionSize.LARGE);
        assertThat(PortionSize.ofWriting("2 Uds. pequeñas o 1 Ud. grande")).isNull();
        assertThat(PortionSize.ofWriting("lechuga")).isNull();
    }

    @Test
    void dropsTheSizeWordsFromAName() {
        assertThat(PortionSize.withoutSize("kiwi mediano")).isEqualTo("kiwi");
        assertThat(PortionSize.withoutSize("plátano pequeño")).isEqualTo("plátano");
        assertThat(PortionSize.withoutSize("Patatas grandes")).isEqualTo("Patatas");
        assertThat(PortionSize.withoutSize("medianoche")).isNull();
        assertThat(PortionSize.withoutSize("lechuga")).isNull();
    }

    @Test
    void readsEverySpellingOfAMeasureAsOneWord() {
        assertThat(HouseholdMeasure.ofUnit("cdta")).contains(HouseholdMeasure.CUCHARADITA);
        assertThat(HouseholdMeasure.ofUnit("Cucharadas soperas")).contains(HouseholdMeasure.CUCHARADA_SOPERA);
        assertThat(HouseholdMeasure.ofUnit("cda")).contains(HouseholdMeasure.CUCHARADA_SOPERA);
        assertThat(HouseholdMeasure.ofUnit("Ud.")).contains(HouseholdMeasure.UNIDAD);
        assertThat(HouseholdMeasure.ofUnit("gr")).isEmpty();
    }

    @Test
    void readsTheLongestMeasureAtTheStartOfAText() {
        assertThat(HouseholdMeasure.readAt("cucharada sopera de aceite"))
                .hasValueSatisfying(reading -> {
                    assertThat(reading.measure()).isEqualTo(HouseholdMeasure.CUCHARADA_SOPERA);
                    assertThat(reading.written()).isEqualTo("cucharada sopera");
                });
        assertThat(HouseholdMeasure.readAt("cucharada de postre")).map(HouseholdMeasure.Reading::measure)
                .contains(HouseholdMeasure.CUCHARADA_POSTRE);
        // A spelling does not end half-way through a word.
        assertThat(HouseholdMeasure.readAt("uvas")).isEmpty();
    }

    @Test
    void writesAMeasureBackInASpellingTheParserReads() {
        assertThat(HouseholdMeasure.UNIDAD.written(false, PortionSize.MEDIUM)).isEqualTo("unidad mediana");
        assertThat(HouseholdMeasure.PLATO.written(true, PortionSize.SMALL)).isEqualTo("platos pequeños");
        assertThat(HouseholdMeasure.CUCHARADA_SOPERA.written(true, null)).isEqualTo("cucharadas soperas");
        for (HouseholdMeasure measure : HouseholdMeasure.values()) {
            assertThat(HouseholdMeasure.ofUnit(measure.written(false, null))).contains(measure);
            assertThat(HouseholdMeasure.ofUnit(measure.written(true, null))).contains(measure);
        }
    }

    @Test
    void preferTheLongestKeywordAndHonourExclusions() {
        assertThat(FoodKeywords.specificity("queso fresco;requeson", "Queso fresco de burgos"))
                .isGreaterThan(FoodKeywords.specificity("queso", "Queso fresco de burgos"));
        assertThat(FoodKeywords.specificity("macarron", "Macarrones, hervidos")).isPositive();
        assertThat(FoodKeywords.specificity("pan", "Panceta, frita")).isEqualTo(-1);
        assertThat(FoodKeywords.specificity("yogur;!liquido", "Yogur líquido, \"tipo actimel\"")).isEqualTo(-1);
        assertThat(FoodKeywords.specificity("yogur;!liquido", "Yogur griego")).isPositive();
        assertThat(FoodKeywords.specificity("", "Lechuga")).isZero();
    }
}
