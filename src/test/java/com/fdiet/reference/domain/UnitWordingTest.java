package com.fdiet.reference.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The unit a patient reads, in both numbers, with its size agreeing. */
class UnitWordingTest {

    @Test
    void aFeminineMeasureAgreesWithItsSizeInBothNumbers() {
        UnitWording wording = UnitWording.of("Huevo, entero, crudo", "unidades", PortionSize.MEDIUM);

        assertThat(wording.singular()).isEqualTo("unidad mediana");
        assertThat(wording.plural()).isEqualTo("unidades medianas");
        assertThat(wording.sizeInName()).isFalse();
    }

    @Test
    void aMasculineMeasureAgreesWithItsSizeInBothNumbers() {
        assertThat(UnitWording.of("Leche", "vaso", PortionSize.SMALL))
                .isEqualTo(new UnitWording("vaso pequeño", "vasos pequeños", false));
        assertThat(UnitWording.of("Lentejas", "platos", PortionSize.MEDIUM))
                .isEqualTo(new UnitWording("plato mediano", "platos medianos", false));
        assertThat(UnitWording.of("Pan", "rebanada", PortionSize.LARGE))
                .isEqualTo(new UnitWording("rebanada grande", "rebanadas grandes", false));
    }

    @Test
    void anAbbreviationIsSpelledOut() {
        assertThat(UnitWording.of("AOVE", "cdta", null))
                .isEqualTo(new UnitWording("cucharadita", "cucharaditas", false));
    }

    @Test
    void aMeasureWithoutASizeIsTheBareWord() {
        assertThat(UnitWording.of("Huevo", "unidad", null))
                .isEqualTo(new UnitWording("unidad", "unidades", false));
    }

    @Test
    void aWeightOrAVolumeIsTheUnitAsStoredInBothNumbers() {
        assertThat(UnitWording.of("lechuga", "gr", null)).isEqualTo(new UnitWording("gr", "gr", false));
        assertThat(UnitWording.of("leche", "ml", null)).isEqualTo(new UnitWording("ml", "ml", false));
    }

    @Test
    void aWordNothingKnowsIsKeptAsStored() {
        assertThat(UnitWording.of("té", "bolsita", PortionSize.SMALL))
                .isEqualTo(new UnitWording("bolsita", "bolsita", false));
    }

    @Test
    void aSizeTheNameAlreadyCarriesIsNotSaidTwice() {
        UnitWording wording = UnitWording.of("kiwi mediano", "unidad", PortionSize.MEDIUM);

        assertThat(wording).isEqualTo(new UnitWording("unidad", "unidades", true));
    }

    @Test
    void noUnitIsNoWording() {
        assertThat(UnitWording.of("sal", null, null)).isEqualTo(new UnitWording(null, null, false));
    }
}
