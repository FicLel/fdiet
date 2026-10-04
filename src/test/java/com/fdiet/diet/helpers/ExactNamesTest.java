package com.fdiet.diet.helpers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one matching rule, shared by the import and the V18 re-match: exact, then
 * exact once more without size words, and nothing less.
 */
class ExactNamesTest {

    private static final Map<String, Long> CROSSWALK = Map.of("kiwi", 1L, "lechuga", 2L, "huevo", 3L);

    private final List<Collection<String>> asked = new ArrayList<>();

    @Test
    void matchesExactlyAndKeysByTheNormalisedName() {
        Map<String, Long> found = ExactNames.resolve(List.of("Lechuga ", "LECHUGA", "tomate"), this::lookup);

        assertThat(found).containsExactly(Map.entry("lechuga", 2L));
        assertThat(asked).containsExactly(List.of("lechuga", "tomate"));
    }

    @Test
    void triesOnceMoreWithoutTheSizeWordsInOneBatch() {
        Map<String, Long> found =
                ExactNames.resolve(List.of("kiwi mediano", "huevo grande", "pera mediana"), this::lookup);

        assertThat(found).containsOnly(Map.entry("kiwi mediano", 1L), Map.entry("huevo grande", 3L));
        assertThat(asked).hasSize(2);
        assertThat(asked.get(1)).containsExactly("kiwi", "huevo", "pera");
    }

    /** Similar is not equal: a name that is only close to a food matches nothing. */
    @Test
    void neverMatchesByResemblance() {
        assertThat(ExactNames.resolve(List.of("lechugas", "kiwis verdes"), this::lookup)).isEmpty();
    }

    @Test
    void asksNothingForBlankNames() {
        assertThat(ExactNames.resolve(List.of(" ", ""), this::lookup)).isEmpty();
        assertThat(asked).isEmpty();
    }

    private Map<String, Long> lookup(Collection<String> names) {
        asked.add(List.copyOf(names));
        Map<String, Long> found = new HashMap<>();
        names.stream().filter(CROSSWALK::containsKey).forEach(name -> found.put(name, CROSSWALK.get(name)));
        return found;
    }
}
