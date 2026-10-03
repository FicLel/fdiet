package com.fdiet.food.helpers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NameIndexTest {

    @Test
    void findsANameExactlyWhateverItsCaseAccentsOrSpacing() {
        NameIndex<Long> index = NameIndex.of(List.of(
                new NameIndex.Entry<>(1L, List.of("Aceite de oliva virgen extra", "AOVE"), true)));

        assertThat(index.find("aove")).isEqualTo(1L);
        assertThat(index.find("  ACEITE  de olivá virgen extra ")).isEqualTo(1L);
        assertThat(index.find("aceite")).isNull();
        assertThat(index.find(null)).isNull();
    }

    @Test
    void givesASharedNameToThePreferredEntry() {
        NameIndex<String> index = NameIndex.of(List.of(
                new NameIndex.Entry<>("CIQUAL 20385", List.of("Tomate, crudo", "tomate"), true),
                new NameIndex.Entry<>("BLS G560100", List.of("Tomate, crudo (BLS)", "tomate"), false)));

        assertThat(index.find("tomate")).isEqualTo("CIQUAL 20385");
        assertThat(index.find("Tomate, crudo (BLS)")).isEqualTo("BLS G560100");
        assertThat(index.conflicts()).isEmpty();
    }

    /** Two foods for one name and nothing saying which: the name answers neither. */
    @Test
    void answersNothingForANameSharedWithoutExactlyOnePreferred() {
        NameIndex<Long> neither = NameIndex.of(List.of(
                new NameIndex.Entry<>(1L, List.of("tomate"), false),
                new NameIndex.Entry<>(2L, List.of("Tomate"), false)));
        NameIndex<Long> both = NameIndex.of(List.of(
                new NameIndex.Entry<>(1L, List.of("tomate"), true),
                new NameIndex.Entry<>(2L, List.of("tomate"), true)));

        assertThat(neither.find("tomate")).isNull();
        assertThat(neither.conflicts()).containsExactly("tomate");
        assertThat(both.find("tomate")).isNull();
        assertThat(both.conflicts()).containsExactly("tomate");
    }

    @Test
    void countsAnEntryNamingItselfTwiceAsOneClaim() {
        NameIndex<Long> index = NameIndex.of(List.of(
                new NameIndex.Entry<>(1L, List.of("Lechuga", "lechuga"), false)));

        assertThat(index.find("lechuga")).isEqualTo(1L);
        assertThat(index.conflicts()).isEmpty();
    }
}
