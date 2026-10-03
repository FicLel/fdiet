package com.fdiet.reference.helpers;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.reference.domain.FoodKeywords;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.RationRole;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.YieldFactorDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Which reference rows cover a food, and when one of them may be attached
 * without asking anybody.
 *
 * <p>It follows the rule the food matching already follows — <em>the machine
 * offers, the nutritionist decides</em> — and adds the one decision a diet has
 * already made: its profile. A household measure is attached on its own only
 * when the choice is not a judgement:
 *
 * <ol>
 *   <li>the measure a person already picked for this ingredient;
 *   <li>the diet's own criterion for that measure and food, when there is one;
 *   <li>else the nutritionist's global criterion for it — hers for every diet,
 *       and the reason a published range such as an egg's weighs at all;
 *   <li>the only row the diet's profile source publishes for it — the profile
 *       is how a diet settles a disagreement between sources;
 *   <li>several published rows that agree to the gram.
 * </ol>
 *
 * <p>Anything else — two sources that disagree, a range ("1 huevo mediano,
 * 53-63 g"), a size the text did not say — is offered and left unattached.
 *
 * <p>Pure: it is handed the rows and looks nothing up, so every rule here is
 * tested without a database.
 */
@Component
public class ReferenceMatcher {

    /**
     * The measure a written ingredient may be weighed by.
     *
     * @param published       every published measure row
     * @param own             the diet's own rows, empty when there is no diet
     * @param global          the nutritionist's global criteria for the food
     * @param category        the food's family, read off its name
     * @param profileSourceCode the source of the diet's profile, or null
     */
    public MeasureChoiceDto chooseMeasure(List<FoodMeasureDto> published, List<FoodMeasureDto> own,
                                          List<FoodMeasureDto> global, MeasureQueryDto query,
                                          FoodCategory category, String profileSourceCode) {
        HouseholdMeasure measure = HouseholdMeasure.ofUnit(query.unit()).orElse(null);
        if (measure == null || !query.namesAFood()) {
            return MeasureChoiceDto.NONE;
        }
        FoodState foodState = FoodState.ofFoodName(query.foodName());
        List<FoodMeasureDto> ownCandidates =
                measureCandidates(own, measure, query, category, foodState);
        List<FoodMeasureDto> globalCandidates =
                measureCandidates(global, measure, query, category, foodState);
        List<FoodMeasureDto> publishedCandidates =
                measureCandidates(published, measure, query, category, foodState);

        List<FoodMeasureDto> all = new ArrayList<>(ownCandidates);
        all.addAll(globalCandidates);
        publishedCandidates.stream()
                .sorted(byProfileThenTier(profileSourceCode))
                .forEach(all::add);
        if (all.isEmpty()) {
            return MeasureChoiceDto.NONE;
        }

        if (query.preferredMeasureId() != null) {
            FoodMeasureDto preferred = all.stream()
                    .filter(row -> row.id().equals(query.preferredMeasureId()))
                    .findFirst().orElse(null);
            if (preferred != null) {
                return new MeasureChoiceDto(preferred, all);
            }
        }

        // A criterion of the nutritionist's decides when there is one: the diet's
        // first, then her global one. Several that weigh differently (sizes the
        // text did not name) are offered, as published rows are.
        for (List<FoodMeasureDto> criteria : List.of(ownCandidates, globalCandidates)) {
            if (!criteria.isEmpty()) {
                List<FoodMeasureDto> weighing = criteria.stream().filter(FoodMeasureDto::weighs).toList();
                return new MeasureChoiceDto(weighing.size() == 1 ? weighing.get(0) : null, all);
            }
        }

        List<FoodMeasureDto> weighing =
                publishedCandidates.stream().filter(FoodMeasureDto::weighs).toList();
        List<FoodMeasureDto> fromProfile = weighing.stream()
                .filter(row -> Objects.equals(row.sourceCode(), profileSourceCode))
                .toList();
        if (fromProfile.size() == 1) {
            return new MeasureChoiceDto(fromProfile.get(0), all);
        }
        if (!weighing.isEmpty() && fromProfile.size() != 1 && agree(weighing)) {
            FoodMeasureDto first = weighing.stream()
                    .sorted(byProfileThenTier(profileSourceCode)).findFirst().orElseThrow();
            return new MeasureChoiceDto(first, all);
        }
        return new MeasureChoiceDto(null, all);
    }

    /**
     * The rations that cover a food, most specific first: the rows naming it
     * outright, then the family rows whose keywords fit its name best.
     */
    public List<RationDto> rationsCovering(List<RationDto> rations, Long compositionFoodId,
                                           String foodName, FoodCategory category) {
        return narrowest(rations.stream()
                .filter(ration -> covers(ration.compositionFoodId(), ration.foodCategory(),
                        ration.keywords(), compositionFoodId, foodName, category))
                .toList(), RationDto::compositionFoodId, RationDto::keywords, compositionFoodId,
                foodName);
    }

    /**
     * The one ration of a profile a food is counted in, or null when there is
     * none or the choice would be a guess.
     *
     * <p>A guideline that sizes a food by its place in the meal (a school main
     * course against a side) has several rows for it; the main-course one is the
     * ration, and a role any narrower is not assumed.
     */
    public RationDto countingRation(List<RationDto> profileRations, Long compositionFoodId,
                                    String foodName, FoodCategory category) {
        List<RationDto> covering = rationsCovering(profileRations, compositionFoodId, foodName,
                category);
        if (covering.size() == 1) {
            return covering.get(0);
        }
        List<RationDto> plain = covering.stream().filter(ration -> ration.role() == null).toList();
        if (plain.size() == 1) {
            return plain.get(0);
        }
        List<RationDto> main = covering.stream()
                .filter(ration -> ration.role() == RationRole.PLATO_PRINCIPAL)
                .toList();
        return main.size() == 1 ? main.get(0) : null;
    }

    /**
     * The cooking yields that cover a food, most specific first, and among those
     * the ones whose method the text names ahead of the rest. All of them are
     * offers: the method a text names is a preference, never a filter, because
     * the source may simply not publish the method a diet cooks by.
     *
     * @param methodText the words the cooked side was written in — the matched
     *                   food's name when it is the cooked one, the ingredient as
     *                   written otherwise
     */
    public List<YieldFactorDto> yieldsCovering(List<YieldFactorDto> yields, String foodName,
                                               FoodCategory category, String methodText) {
        List<YieldFactorDto> covering = narrowest(yields.stream()
                .filter(row -> covers(null, row.foodCategory(), row.keywords(), null, foodName,
                        category))
                .toList(), row -> null, YieldFactorDto::keywords, null, foodName);
        return covering.stream()
                .sorted(Comparator.comparing((YieldFactorDto row) -> !namesMethod(row, methodText)))
                .toList();
    }

    /** Whether the text names the row's method in one of its Spanish words. */
    public static boolean namesMethod(YieldFactorDto row, String methodText) {
        return methodText != null && row.methodKeywords() != null
                && !FoodKeywords.phrases(row.methodKeywords()).isEmpty()
                && FoodKeywords.specificity(row.methodKeywords(), methodText) > 0;
    }

    private List<FoodMeasureDto> measureCandidates(List<FoodMeasureDto> rows, HouseholdMeasure measure,
                                                   MeasureQueryDto query, FoodCategory category,
                                                   FoodState foodState) {
        List<FoodMeasureDto> covering = rows.stream()
                .filter(row -> row.measure() == measure)
                .filter(row -> covers(row.compositionFoodId(), row.foodCategory(), row.keywords(),
                        query.compositionFoodId(), query.foodName(), category))
                .filter(row -> query.size() == null || row.size() == null || row.size() == query.size())
                .filter(row -> !FoodState.disagree(row.state(), foodState))
                .toList();
        covering = narrowest(covering, FoodMeasureDto::compositionFoodId, FoodMeasureDto::keywords,
                query.compositionFoodId(), query.foodName());
        PortionSize size = query.size();
        if (size != null && covering.stream().anyMatch(row -> row.size() == size)) {
            covering = covering.stream().filter(row -> row.size() == size).toList();
        }
        return covering;
    }

    /**
     * Rows that name the food outright beat rows that reach it through its
     * family; among family rows, the most specific keywords win.
     */
    private static <T> List<T> narrowest(List<T> rows, Function<T, Long> foodIdOf,
                                         Function<T, String> keywordsOf, Long compositionFoodId,
                                         String foodName) {
        List<T> named = rows.stream()
                .filter(row -> foodIdOf.apply(row) != null
                        && foodIdOf.apply(row).equals(compositionFoodId))
                .toList();
        if (!named.isEmpty()) {
            return named;
        }
        int best = rows.stream()
                .mapToInt(row -> FoodKeywords.specificity(keywordsOf.apply(row), foodName))
                .max().orElse(-1);
        return rows.stream()
                .filter(row -> FoodKeywords.specificity(keywordsOf.apply(row), foodName) == best)
                .toList();
    }

    /**
     * A row naming a food covers that composition food and nothing else — never
     * a food known only by its name, whose id (a BEDCA one, until FD-033 phase D)
     * is not a composition id. A family row covers the names its keywords fit.
     */
    private static boolean covers(Long rowFoodId, FoodCategory rowCategory, String rowKeywords,
                                  Long compositionFoodId, String foodName, FoodCategory category) {
        if (rowFoodId != null) {
            return rowFoodId.equals(compositionFoodId);
        }
        if (rowCategory == null || rowCategory != category) {
            return false;
        }
        return FoodKeywords.specificity(rowKeywords, foodName) >= 0;
    }

    private static boolean agree(List<FoodMeasureDto> rows) {
        BigDecimal first = rows.get(0).gramsPerMeasure();
        return rows.stream().allMatch(row -> row.gramsPerMeasure().compareTo(first) == 0);
    }

    private static Comparator<FoodMeasureDto> byProfileThenTier(String profileSourceCode) {
        return Comparator
                .comparing((FoodMeasureDto row) -> !Objects.equals(row.sourceCode(), profileSourceCode))
                .thenComparing(row -> row.sourceTier() == null ? Integer.MAX_VALUE : row.sourceTier())
                .thenComparing(FoodMeasureDto::id);
    }
}
