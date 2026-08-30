package com.fdiet.alternative.helpers;

import com.fdiet.food.dto.NutritionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The figures are the real per-100 g ones, rounded, for the foods named.
 */
class NutritionSimilarityTest {

    /** Pollo, pechuga, plancha (f_id 2297). */
    private static final NutritionDto CHICKEN_BREAST = of("165", "31", "3.6", "0", "0");

    /** Merluza, congelada, cruda — the swap a diet actually makes. */
    private static final NutritionDto HAKE = of("71", "15.9", "0.8", "0", "0");

    /** Cerdo, solomillo, asado — the same shelf, and much the nearer figure. */
    private static final NutritionDto PORK_LOIN = of("158", "30", "4.1", "0", "0");

    private final NutritionSimilarity similarity = new NutritionSimilarity();

    @Test
    void scoresAFoodAgainstItselfAsAHundred() {
        assertThat(similarity.score(CHICKEN_BREAST, CHICKEN_BREAST)).isEqualTo(100);
    }

    @Test
    void putsTheNearerCompositionAhead() {
        assertThat(similarity.score(CHICKEN_BREAST, PORK_LOIN))
                .isGreaterThan(similarity.score(CHICKEN_BREAST, HAKE));
    }

    @Test
    void staysOnTheScale() {
        NutritionDto oil = of("900", "0", "100", "0", "0");
        assertThat(similarity.score(CHICKEN_BREAST, oil)).isBetween(0, 100);
    }

    /**
     * A gram of fat against two is a rounding difference, not one food being
     * twice as fatty as another. The floor is what says so.
     */
    @Test
    void doesNotTreatSmallAbsoluteDifferencesAsLargeOnes() {
        NutritionDto lean = of("165", "31", "1", "0", "0");
        NutritionDto leaner = of("165", "31", "2", "0", "0");
        assertThat(similarity.score(lean, leaner)).isGreaterThanOrEqualTo(95);
    }

    /** A missing figure is not a zero, so it is not a difference either. */
    @Test
    void comparesOnlyWhatBothFoodsPublish() {
        NutritionDto withoutFibre = of("165", "31", "3.6", "0", null);
        assertThat(similarity.score(CHICKEN_BREAST, withoutFibre)).isEqualTo(100);
    }

    @Test
    void refusesToRankOnASingleComponent() {
        NutritionDto energyOnly = of("165", null, null, null, null);
        assertThat(similarity.score(CHICKEN_BREAST, energyOnly)).isNull();
        assertThat(similarity.score(energyOnly, CHICKEN_BREAST)).isNull();
    }

    @Test
    void refusesAFoodNothingIsKnownAbout() {
        assertThat(similarity.score(CHICKEN_BREAST, NutritionDto.EMPTY)).isNull();
        assertThat(similarity.score(CHICKEN_BREAST, null)).isNull();
    }

    private static NutritionDto of(String kcal, String protein, String fat, String carbs, String fibre) {
        return new NutritionDto(
                decimal(kcal), decimal(protein), decimal(fat), null,
                decimal(carbs), null, decimal(fibre), null);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
