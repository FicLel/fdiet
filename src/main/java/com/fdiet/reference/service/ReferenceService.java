package com.fdiet.reference.service;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.HouseholdMeasureDto;
import com.fdiet.reference.dto.MealShareDto;
import com.fdiet.reference.dto.MealSharesDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureCriterionRequestDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.RecommendationDto;
import com.fdiet.reference.dto.ReferenceProfileDetailDto;
import com.fdiet.reference.dto.ReferenceProfileDto;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto;
import com.fdiet.reference.dto.YieldFactorDto;
import com.fdiet.reference.exception.InvalidReferenceException;
import com.fdiet.reference.exception.ReferenceNotFoundException;
import com.fdiet.reference.helpers.ReferenceMatcher;
import com.fdiet.reference.mapper.IReferenceMapper;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReferenceService implements IReferenceService {

    /** Somebody this old reads against the adult profile, whatever else covers the age. */
    private static final int ADULT_MONTHS = 18 * 12;

    private final ReferenceTables tables;
    private final IReferenceMapper mapper;
    private final ReferenceMatcher matcher;
    private final IFoodCategoriser categoriser;
    private final ICompositionFoodService compositionFoodService;
    private final IBedcaFoodService bedcaFoodService;
    private final IMeasureCriterionService criteria;
    private final String defaultAdultProfile;

    /**
     * The published rows, read whole: a few hundred of them, read on every
     * weighing. Dropped whenever a sync writes. Holds transport records only —
     * never entities, which would be detached by the time anybody read them.
     */
    private volatile ReferenceSnapshot snapshot;

    public ReferenceService(ReferenceTables tables,
                            IReferenceMapper mapper,
                            ReferenceMatcher matcher,
                            IFoodCategoriser categoriser,
                            ICompositionFoodService compositionFoodService,
                            IBedcaFoodService bedcaFoodService,
                            IMeasureCriterionService criteria,
                            @Value("${fdiet.reference.default-adult-profile:AESAN-2022:ADULTOS}")
                            String defaultAdultProfile) {
        this.tables = tables;
        this.mapper = mapper;
        this.matcher = matcher;
        this.categoriser = categoriser;
        this.compositionFoodService = compositionFoodService;
        this.bedcaFoodService = bedcaFoodService;
        this.criteria = criteria;
        this.defaultAdultProfile = defaultAdultProfile;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceSourceDto> sources() {
        return snapshot().sources();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceProfileDto> profiles(Integer ageMonths) {
        String suggested = ageMonths == null ? null : suggestedProfileCode(ageMonths);
        return snapshot().populations().values().stream()
                .filter(PopulationView::selectable)
                .sorted(PopulationView.ORDER)
                .map(view -> view.profile(view.code().equals(suggested)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReferenceProfileDetailDto profile(String code) {
        PopulationView view = population(code);
        List<RationDto> rations = rations(code);
        List<RecommendationDto> recommendations = recommendations(code);
        MealSharesDto shares = mealShares(code);

        Set<String> sourceCodes = new LinkedHashSet<>();
        sourceCodes.add(view.sourceCode());
        if (shares != null) {
            sourceCodes.add(shares.sourceCode());
        }
        List<ReferenceSourceDto> sources = snapshot().sources().stream()
                .filter(source -> sourceCodes.contains(source.code()))
                .toList();
        return new ReferenceProfileDetailDto(view.profile(false), rations, recommendations, shares,
                sources);
    }

    @Override
    @Transactional(readOnly = true)
    public String suggestedProfileCode(Integer ageMonths) {
        Map<String, PopulationView> populations = snapshot().populations();
        PopulationView adult = populations.get(defaultAdultProfile);
        if (ageMonths == null || ageMonths >= ADULT_MONTHS) {
            return adult != null && adult.selectable() ? adult.code() : null;
        }
        return populations.values().stream()
                .filter(PopulationView::selectable)
                .filter(view -> view.covers(ageMonths))
                .sorted(PopulationView.ORDER)
                .map(PopulationView::code)
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean profileExists(String code) {
        PopulationView view = code == null ? null : snapshot().populations().get(code);
        return view != null && view.selectable();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RationDto> rations(String profileCode) {
        population(profileCode);
        return snapshot().rations().stream()
                .filter(ration -> ration.profileCode().equals(profileCode))
                .toList();
    }

    /**
     * The profile's rows that cover the food, then the per-food rows of sources
     * that are not a profile at all (5 al día's table is a ration per fruit, not
     * a population to write a diet for). Another profile's rows are left out:
     * those are a different population, and one diet is read against one.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RationDto> rationsForFood(String profileCode, Long compositionFoodId,
                                          Long bedcaFoodId) {
        if (compositionFoodId == null && bedcaFoodId == null) {
            return rations(profileCode);
        }
        LookedUpFood food = food(compositionFoodId, bedcaFoodId);
        FoodCategory category = categoryOf(food.name());
        ReferenceSnapshot current = snapshot();

        List<RationDto> answer = new ArrayList<>();
        if (profileCode != null) {
            population(profileCode);
            answer.addAll(matcher.rationsCovering(current.rations().stream()
                    .filter(ration -> ration.profileCode().equals(profileCode))
                    .toList(), food.compositionFoodId(), food.name(), category));
        }
        List<RationDto> perFood = current.rations().stream()
                .filter(ration -> ration.compositionFoodId() != null || ration.keywords() != null)
                .filter(ration -> {
                    PopulationView view = current.populations().get(ration.profileCode());
                    return view != null && !view.selectable();
                })
                .toList();
        answer.addAll(matcher.rationsCovering(perFood, food.compositionFoodId(), food.name(), category));
        return answer;
    }

    @Override
    @Transactional(readOnly = true)
    public RationDto countingRation(String profileCode, Long compositionFoodId, String foodName) {
        if (profileCode == null || (compositionFoodId == null && foodName == null)) {
            return null;
        }
        List<RationDto> profileRations = snapshot().rations().stream()
                .filter(ration -> ration.profileCode().equals(profileCode))
                .toList();
        return matcher.countingRation(profileRations, compositionFoodId, foodName,
                categoryOf(foodName));
    }

    @Override
    @Transactional(readOnly = true)
    public ReferenceProfileDto profileSummary(String code) {
        PopulationView view = code == null ? null : snapshot().populations().get(code);
        return view == null ? null : view.profile(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecommendationDto> recommendations(String profileCode) {
        population(profileCode);
        return snapshot().recommendations().getOrDefault(profileCode, List.of());
    }

    /**
     * The profile's own shares, or the ones it names as borrowed. A borrowed set
     * always carries the note that says it is borrowed.
     */
    @Override
    @Transactional(readOnly = true)
    public MealSharesDto mealShares(String profileCode) {
        PopulationView view = population(profileCode);
        ReferenceSnapshot current = snapshot();
        String from = current.shares().containsKey(profileCode) ? profileCode : view.mealSharesFrom();
        if (from == null || !current.shares().containsKey(from)) {
            return null;
        }
        PopulationView lender = current.populations().get(from);
        boolean borrowed = !lender.sourceCode().equals(view.sourceCode());
        List<MealShareDto> shares = current.shares().get(from);
        return new MealSharesDto(
                lender.code(),
                lender.label(),
                lender.sourceCode(),
                lender.sourceShortName(),
                borrowed,
                from.equals(profileCode) ? null : view.mealSharesNote(),
                current.sharePages().get(from),
                shares);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExchangeSystemDto> exchangeSystems(boolean includeClinical) {
        return snapshot().exchanges().stream()
                .filter(system -> includeClinical || !system.clinical())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<YieldFactorDto> yieldFactors(String foodName, String methodText) {
        if (foodName == null) {
            return List.of();
        }
        return matcher.yieldsCovering(snapshot().yields(), foodName, categoryOf(foodName),
                methodText);
    }

    @Override
    @Transactional(readOnly = true)
    public List<YieldFactorDto> yieldFactorsForFood(Long compositionFoodId, Long bedcaFoodId) {
        String name = food(compositionFoodId, bedcaFoodId).name();
        return yieldFactors(name, name);
    }

    @Override
    public List<HouseholdMeasureDto> vocabulary() {
        return Arrays.stream(HouseholdMeasure.values())
                .map(measure -> new HouseholdMeasureDto(measure, measure.label(), measure.aliases()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodMeasureDto> measuresForFood(Long compositionFoodId, Long bedcaFoodId,
                                                String unit, Long dietId, String profileCode) {
        LookedUpFood food = food(compositionFoodId, bedcaFoodId);
        ReferenceSnapshot current = snapshot();
        List<FoodMeasureDto> own = dietId == null ? List.of() : criteria.dietRows(dietId);
        List<FoodMeasureDto> global = food.compositionFoodId() == null
                ? List.of()
                : criteria.globalRows(List.of(food.compositionFoodId()));
        PopulationView profile = profileCode == null ? null : current.populations().get(profileCode);
        String profileSource = profile == null ? null : profile.sourceCode();
        FoodCategory category = categoryOf(food.name());

        List<String> units = unit != null
                ? List.of(unit)
                : Arrays.stream(HouseholdMeasure.values()).map(HouseholdMeasure::label).toList();
        List<FoodMeasureDto> all = new ArrayList<>();
        for (String written : units) {
            all.addAll(matcher.chooseMeasure(current.measures(), own, global,
                    new MeasureQueryDto(food.compositionFoodId(), food.name(), written, null, null),
                    category, profileSource).candidates());
        }
        return all;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasureChoiceDto> chooseMeasures(List<MeasureQueryDto> queries, Long dietId,
                                                 String profileCode) {
        if (queries.isEmpty()) {
            return List.of();
        }
        ReferenceSnapshot current = snapshot();
        List<FoodMeasureDto> own = dietId == null ? List.of() : criteria.dietRows(dietId);
        List<FoodMeasureDto> global = criteria.globalRows(queries.stream()
                .map(MeasureQueryDto::compositionFoodId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        PopulationView profile = profileCode == null ? null : current.populations().get(profileCode);
        String profileSource = profile == null ? null : profile.sourceCode();

        return queries.stream()
                .map(query -> !query.namesAFood()
                        ? MeasureChoiceDto.NONE
                        : matcher.chooseMeasure(current.measures(), own, global, query,
                        categoryOf(query.foodName()), profileSource))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ReferenceFoodMeasure> measureEntities(Collection<Long> ids) {
        return tables.measureEntities(ids);
    }

    @Override
    public FoodMeasureDto describe(ReferenceFoodMeasure measure) {
        return measure == null ? null : mapper.toDto(measure);
    }

    @Override
    public List<FoodMeasureDto> dietMeasures(Long dietId) {
        return criteria.dietRows(dietId);
    }

    @Override
    public FoodMeasureDto saveDietMeasure(Long dietId, MeasureCriterionRequestDto request) {
        return criteria.saveDietMeasure(dietId, request);
    }

    @Override
    public void deleteDietMeasure(Long dietId, Long measureId) {
        criteria.deleteDietMeasure(dietId, measureId);
    }

    @Override
    public Map<Long, Long> copyDietMeasures(Long fromDietId, Long toDietId) {
        return criteria.copyDietMeasures(fromDietId, toDietId);
    }

    @Override
    @Transactional
    public ReferenceSyncSummaryDto store(ReferenceRowsDto rows) {
        ReferenceSyncSummaryDto summary = tables.store(rows);
        snapshot = null;
        return summary;
    }

    private PopulationView population(String code) {
        PopulationView view = code == null ? null : snapshot().populations().get(code);
        if (view == null) {
            throw ReferenceNotFoundException.profile(code);
        }
        return view;
    }

    private ReferenceSnapshot snapshot() {
        ReferenceSnapshot current = snapshot;
        if (current == null) {
            current = tables.load();
            snapshot = current;
        }
        return current;
    }

    /**
     * The food a lookup names: a composition food — its id, and its Spanish name
     * when the crosswalk gives one — or, until FD-033 phase D re-matches every
     * ingredient, a BEDCA food by its name only. The BEDCA id goes no further
     * than this: it is never compared with the composition id a row names.
     */
    private LookedUpFood food(Long compositionFoodId, Long bedcaFoodId) {
        if ((compositionFoodId == null) == (bedcaFoodId == null)) {
            throw new InvalidReferenceException(
                    "Name the food by exactly one of compositionFoodId and bedcaFoodId");
        }
        if (compositionFoodId != null) {
            CompositionFood food = compositionFoodService.entityById(compositionFoodId);
            return new LookedUpFood(food.getId(), food.getNameEs());
        }
        return new LookedUpFood(null, bedcaFoodService.entityById(bedcaFoodId).getName());
    }

    /** The family a food name reads as; null without a name. */
    private FoodCategory categoryOf(String foodName) {
        return foodName == null ? null : categoriser.of(foodName);
    }

    private record LookedUpFood(Long compositionFoodId, String name) {
    }
}
