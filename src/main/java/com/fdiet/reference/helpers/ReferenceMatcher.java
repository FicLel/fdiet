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
     * @param category        the food's family, read off its name
     * @param profileSourceCode the source of the diet's profile, or null
     */
    public MeasureChoiceDto chooseMeasure(List<FoodMeasureDto> published, List<FoodMeasureDto> own,
                                          MeasureQueryDto query, FoodCategory category,
                                          String profileSourceCode) {
        HouseholdMeasure measure = HouseholdMeasure.ofUnit(query.unit()).orElse(null);
        if (measure == null || query.bedcaFoodId() == null) {
            return MeasureChoiceDto.NONE;
        }
        FoodState foodState = FoodState.ofFoodName(query.foodName());
        List<FoodMeasureDto> ownCandidates =
                measureCandidates(own, measure, query, category, foodState);
        List<FoodMeasureDto> publishedCandidates =
                measureCandidates(published, measure, query, category, foodState);

        List<FoodMeasureDto> all = new ArrayList<>(ownCandidates);
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

        if (!ownCandidates.isEmpty()) {
            List<FoodMeasureDto> weighing = ownCandidates.stream().filter(FoodMeasureDto::weighs).toList();
            return new MeasureChoiceDto(weighing.size() == 1 ? weighing.get(0) : null, all);
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
    public List<RationDto> rationsCovering(List<RationDto> rations, Long bedcaFoodId,
                                           String foodName, FoodCategory category) {
        return narrowest(rations.stream()
                .filter(ration -> covers(ration.bedcaFoodId(), ration.foodCategory(),
                        ration.keywords(), bedcaFoodId, foodName, category))
                .toList(), RationDto::bedcaFoodId, RationDto::keywords, bedcaFoodId, foodName);
    }

    /**
     * The one ration of a profile a food is counted in, or null when there is
     * none or the choice would be a guess.
     *
     * <p>A guideline that sizes a food by its place in the meal (a school main
     * course against a side) has several rows for it; the main-course one is the
     * ration, and a role any narrower is not assumed.
     */
    public RationDto countingRation(List<RationDto> profileRations, Long bedcaFoodId,
                                    String foodName, FoodCategory category) {
        List<RationDto> covering = rationsCovering(profileRations, bedcaFoodId, foodName, category);
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

    private List<FoodMeasureDto> measureCandidates(List<FoodMeasureDto> rows, HouseholdMeasure measure,
                                                   MeasureQueryDto query, FoodCategory category,
                                                   FoodState foodState) {
        List<FoodMeasureDto> covering = rows.stream()
                .filter(row -> row.measure() == measure)
                .filter(row -> covers(row.bedcaFoodId(), row.foodCategory(), row.keywords(),
                        query.bedcaFoodId(), query.foodName(), category))
                .filter(row -> query.size() == null || row.size() == null || row.size() == query.size())
                .filter(row -> !FoodState.disagree(row.state(), foodState))
                .toList();
        covering = narrowest(covering, FoodMeasureDto::bedcaFoodId, FoodMeasureDto::keywords,
                query.bedcaFoodId(), query.foodName());
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
                                         Function<T, String> keywordsOf, Long bedcaFoodId,
                                         String foodName) {
        List<T> named = rows.stream()
                .filter(row -> foodIdOf.apply(row) != null && foodIdOf.apply(row).equals(bedcaFoodId))
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

    private static boolean covers(Long rowFoodId, FoodCategory rowCategory, String rowKeywords,
                                  Long bedcaFoodId, String foodName, FoodCategory category) {
        if (rowFoodId != null) {
            return rowFoodId.equals(bedcaFoodId);
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
