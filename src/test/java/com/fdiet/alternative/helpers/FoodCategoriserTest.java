package com.fdiet.alternative.helpers;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.food.helpers.DataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Every name here is one bedca_foods.csv actually carries.
 */
class FoodCategoriserTest {

    private static final Path CSV = Path.of("bedca_foods.csv");

    /** {@code f_ori_name}, the column the category is read off. */
    private static final int NAME = 1;

    private final FoodCategoriser categoriser = new FoodCategoriser();

    @ParameterizedTest
    @CsvSource({
            "'Pollo, pechuga, plancha',              MEAT",
            "'Merluza fresca',                       FISH",
            "'Lechuga',                              VEGETABLE",
            "'Lenteja, seca, cruda',                 LEGUME",
            "'Aguacate',                             FRUIT",
            "'Patata, hervida',                      TUBER",
            "'Pan integral',                         CEREAL",
            "'Almendra, cruda',                      NUT",
    })
    void readsTheFoodOffTheHeadOfTheName(String name, FoodCategory expected) {
        assertThat(categoriser.of(name)).isEqualTo(expected);
    }

    /**
     * The head of a BEDCA name is the food and the rest is what was done to it,
     * so a later word never gets to overrule the first one that is recognised.
     */
    @ParameterizedTest
    @CsvSource({
            // Not offal, and not fish.
            "'Aceite de hígado de bacalao',          FAT_OIL",
            // Not a dairy product.
            "'Café, con leche',                      BEVERAGE",
            // Not an egg.
            "'Flan de huevo',                        SWEET",
            // Not a legume, and not a cheese.
            "'Salsa de queso',                       CONDIMENT",
            // Not stock.
            "'Cubito de caldo',                      CONDIMENT",
            // Not a vegetable.
            "'Puré de patata, con leche',            PREPARED_DISH",
            // Not beef.
            "'Corazón, de vaca/buey, cocido',        MEAT",
            // Not bread.
            "'Pan de leche',                         CEREAL",
            // Not butter.
            "'Galletas, de mantequilla',             CEREAL",
    })
    void takesTheFirstWordAnyRuleClaims(String name, FoodCategory expected) {
        assertThat(categoriser.of(name)).isEqualTo(expected);
    }

    /** The few foods named after something they are not. */
    @ParameterizedTest
    @CsvSource({
            "'Judía verde, cruda',                   VEGETABLE",
            "'Judias verdes en conserva',            VEGETABLE",
            "'Nuez moscada',                         CONDIMENT",
            "'Frutos secos',                         NUT",
    })
    void readsTheTwoWordNamesAsTheFoodTheyAre(String name, FoodCategory expected) {
        assertThat(categoriser.of(name)).isEqualTo(expected);
    }

    /** Spanish plurals, and the words a naive rule would strip too far. */
    @ParameterizedTest
    @CsvSource({
            "'Guisantes en conserva',                LEGUME",
            "'Berberechos en conserva',              FISH",
            "'Anís, seco',                           BEVERAGE",
            "'Sesos, de ternera, crudos',            MEAT",
            "'Callos de ternera',                    MEAT",
            "'Zamburiñas',                           FISH",
            "'Pipas de girasol, peladas, con sal',   NUT",
    })
    void foldsPluralsOntoTheWordTheRuleIsWrittenWith(String name, FoodCategory expected) {
        assertThat(categoriser.of(name)).isEqualTo(expected);
    }

    @Test
    void refusesANameNoRuleClaims() {
        assertThat(categoriser.of("Cremoso san millan")).isNull();
        assertThat(categoriser.of("")).isNull();
        assertThat(categoriser.of(null)).isNull();
    }

    /**
     * The whole composition database, so a rule that stops claiming a shelf of
     * foods shows up here rather than as an empty list in production.
     */
    @Test
    void claimsAllButAHandfulOfTheCompositionDatabase() {
        assumeThat(Files.exists(CSV)).isTrue();
        List<List<String>> records = new DataReader(CSV.toString()).read(CSV);
        List<String> names = records.subList(1, records.size()).stream()
                .filter(record -> record.size() > NAME)
                .map(record -> record.get(NAME))
                .toList();

        List<String> unclaimed = names.stream()
                .filter(name -> categoriser.of(name) == null)
                .toList();

        assertThat(names).hasSize(957);
        assertThat(unclaimed).containsExactly("Cremoso san millan");
    }
}
