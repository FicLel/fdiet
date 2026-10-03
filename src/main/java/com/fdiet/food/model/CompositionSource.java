package com.fdiet.food.model;

/**
 * The open composition tables {@code composition_foods} is loaded from, in the
 * order they answer: CIQUAL first, BLS for the gaps.
 *
 * <p>Every food carries its source because the methods differ — CIQUAL's protein
 * uses Jones factors and BLS's uses 6.25, BLS's energy is always its own formula
 * — so a figure is only read correctly beside the name of the table it came
 * from. The attribution is the one each licence (CC BY 4.0) requires wherever
 * the figures are shown; see {@code reference-data/composition/}.
 */
public enum CompositionSource {

    CIQUAL("CIQUAL 2025",
            "ANSES. Ciqual French food composition table 2025. https://ciqual.anses.fr/ — "
                    + "doi:10.5281/zenodo.17550133. CC BY 4.0."),
    BLS("BLS 4.0",
            "Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0 — "
                    + "Deutsche Nährstoffdatenbank. Karlsruhe. DOI: 10.25826/Data20251217-134202-0. "
                    + "CC BY 4.0.");

    private final String label;
    private final String attribution;

    CompositionSource(String label, String attribution) {
        this.label = label;
        this.attribution = attribution;
    }

    /** A short name for a screen: {@code CIQUAL 2025}. */
    public String label() {
        return label;
    }

    /** The attribution the licence requires wherever these figures are shown. */
    public String attribution() {
        return attribution;
    }
}
