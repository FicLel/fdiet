package com.fdiet.reference.domain;

import com.fdiet.common.helper.Texts;

/**
 * How a unit is said to the person reading the plate, in both numbers, the size
 * agreeing with the measure: {@code unidad mediana} / {@code unidades medianas},
 * {@code vaso pequeño} / {@code vasos pequeños}. Derived on read and never stored.
 *
 * <p>The reader picks one by the quantity served — the singular for exactly one,
 * the plural otherwise — so the gender and plural of a measure stay here, beside
 * {@link HouseholdMeasure}, and nothing else has to know them.
 *
 * <p>A unit that is no household measure — a weight ({@code g}, {@code ml}) or a
 * word the vocabulary does not know — is both numbers as stored: there is no
 * plural to invent and no size it can agree with.
 *
 * <p>{@code sizeInName} says the ingredient's name already carries a size word
 * ({@code 1 kiwi mediano} keeps the name {@code kiwi mediano}). The size is then
 * left out of the unit, so {@code quantity + unit + name} says it once.
 */
public record UnitWording(String singular, String plural, boolean sizeInName) {

    /** The wording of {@code unit} for an ingredient called {@code name}, read with {@code size}. */
    public static UnitWording of(String name, String unit, PortionSize size) {
        boolean sizeInName = PortionSize.withoutSize(name) != null;
        String stored = Texts.trimToNull(unit);
        if (stored == null) {
            return new UnitWording(null, null, sizeInName);
        }
        PortionSize agreeing = sizeInName ? null : size;
        return HouseholdMeasure.ofUnit(stored)
                .map(measure -> new UnitWording(
                        measure.written(false, agreeing), measure.written(true, agreeing), sizeInName))
                .orElseGet(() -> new UnitWording(stored, stored, sizeInName));
    }
}
