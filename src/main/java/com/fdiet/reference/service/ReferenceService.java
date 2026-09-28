package com.fdiet.reference.service;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.DietMeasureRequestDto;
import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.HouseholdMeasureDto;
import com.fdiet.reference.dto.MealShareDto;
import com.fdiet.reference.dto.MealSharesDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.RecommendationDto;
import com.fdiet.reference.dto.ReferenceProfileDetailDto;
import com.fdiet.reference.dto.ReferenceProfileDto;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto.TableSync;
import com.fdiet.reference.exception.InvalidReferenceException;
import com.fdiet.reference.exception.ReferenceNotFoundException;
import com.fdiet.reference.helpers.ReferenceMatcher;
import com.fdiet.reference.mapper.IReferenceMapper;
import com.fdiet.reference.model.ReferenceExchangeSystem;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.model.ReferenceMealShare;
import com.fdiet.reference.model.ReferencePopulation;
import com.fdiet.reference.model.ReferenceRation;
import com.fdiet.reference.model.ReferenceRecommendation;
import com.fdiet.reference.model.ReferenceSource;
import com.fdiet.reference.repository.ReferenceExchangeSystemRepository;
import com.fdiet.reference.repository.ReferenceFoodMeasureRepository;
import com.fdiet.reference.repository.ReferenceMealShareRepository;
import com.fdiet.reference.repository.ReferencePopulationRepository;
import com.fdiet.reference.repository.ReferenceRationRepository;
import com.fdiet.reference.repository.ReferenceRecommendationRepository;
import com.fdiet.reference.repository.ReferenceSourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class ReferenceService implements IReferenceService {

    /** Somebody this old reads against the adult profile, whatever else covers the age. */
    private static final int ADULT_MONTHS = 18 * 12;

    private static final int NOTE_MAX = 500;
    private static final int LABEL_MAX = 160;

    private final ReferenceSourceRepository sourceRepository;
    private final ReferencePopulationRepository populationRepository;
    private final ReferenceRationRepository rationRepository;
    private final ReferenceFoodMeasureRepository measureRepository;
    private final ReferenceRecommendationRepository recommendationRepository;
    private final ReferenceMealShareRepository mealShareRepository;
    private final ReferenceExchangeSystemRepository exchangeSystemRepository;
    private final IReferenceMapper mapper;
    private final ReferenceMatcher matcher;
    private final IFoodCategoriser categoriser;
    private final IBedcaFoodService bedcaFoodService;
    private final String defaultAdultProfile;

    /**
     * The published rows, read whole: a few hundred of them, read on every
     * weighing. Dropped whenever a sync writes. Holds transport records only —
     * never entities, which would be detached by the time anybody read them.
     */
    private volatile Snapshot snapshot;

    public ReferenceService(ReferenceSourceRepository sourceRepository,
                            ReferencePopulationRepository populationRepository,
                            ReferenceRationRepository rationRepository,
                            ReferenceFoodMeasureRepository measureRepository,
                            ReferenceRecommendationRepository recommendationRepository,
                            ReferenceMealShareRepository mealShareRepository,
                            ReferenceExchangeSystemRepository exchangeSystemRepository,
                            IReferenceMapper mapper,
                            ReferenceMatcher matcher,
                            IFoodCategoriser categoriser,
                            IBedcaFoodService bedcaFoodService,
                            @Value("${fdiet.reference.default-adult-profile:AESAN-2022:ADULTOS}")
                            String defaultAdultProfile) {
        this.sourceRepository = sourceRepository;
        this.populationRepository = populationRepository;
        this.rationRepository = rationRepository;
        this.measureRepository = measureRepository;
        this.recommendationRepository = recommendationRepository;
        this.mealShareRepository = mealShareRepository;
        this.exchangeSystemRepository = exchangeSystemRepository;
        this.mapper = mapper;
        this.matcher = matcher;
        this.categoriser = categoriser;
        this.bedcaFoodService = bedcaFoodService;
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
    public List<RationDto> rationsForFood(String profileCode, Long bedcaFoodId) {
        if (bedcaFoodId == null) {
            return rations(profileCode);
        }
        BedcaFood food = bedcaFoodService.entityById(bedcaFoodId);
        FoodCategory category = categoriser.of(food.getName());
        Snapshot current = snapshot();

        List<RationDto> answer = new ArrayList<>();
        if (profileCode != null) {
            population(profileCode);
            answer.addAll(matcher.rationsCovering(current.rations().stream()
                    .filter(ration -> ration.profileCode().equals(profileCode))
                    .toList(), food.getId(), food.getName(), category));
        }
        List<RationDto> perFood = current.rations().stream()
                .filter(ration -> ration.bedcaFoodId() != null || ration.keywords() != null)
                .filter(ration -> {
                    PopulationView view = current.populations().get(ration.profileCode());
                    return view != null && !view.selectable();
                })
                .toList();
        answer.addAll(matcher.rationsCovering(perFood, food.getId(), food.getName(), category));
        return answer;
    }

    @Override
    @Transactional(readOnly = true)
    public RationDto countingRation(String profileCode, Long bedcaFoodId, String foodName) {
        if (profileCode == null || bedcaFoodId == null || foodName == null) {
            return null;
        }
        List<RationDto> profileRations = snapshot().rations().stream()
                .filter(ration -> ration.profileCode().equals(profileCode))
                .toList();
        return matcher.countingRation(profileRations, bedcaFoodId, foodName,
                categoriser.of(foodName));
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
        Snapshot current = snapshot();
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
    public List<HouseholdMeasureDto> vocabulary() {
        return Arrays.stream(HouseholdMeasure.values())
                .map(measure -> new HouseholdMeasureDto(measure, measure.label(), measure.aliases()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodMeasureDto> measuresForFood(Long bedcaFoodId, String unit, Long dietId,
                                                String profileCode) {
        BedcaFood food = bedcaFoodService.entityById(bedcaFoodId);
        Snapshot current = snapshot();
        List<FoodMeasureDto> own = dietId == null ? List.of() : dietMeasures(dietId);
        PopulationView profile = profileCode == null ? null : current.populations().get(profileCode);
        String profileSource = profile == null ? null : profile.sourceCode();
        FoodCategory category = categoriser.of(food.getName());

        List<String> units = unit != null
                ? List.of(unit)
                : Arrays.stream(HouseholdMeasure.values()).map(HouseholdMeasure::label).toList();
        List<FoodMeasureDto> all = new ArrayList<>();
        for (String written : units) {
            all.addAll(matcher.chooseMeasure(current.measures(), own,
                    new MeasureQueryDto(food.getId(), food.getName(), written, null, null),
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
        Snapshot current = snapshot();
        List<FoodMeasureDto> own = dietId == null ? List.of() : dietMeasures(dietId);
        PopulationView profile = profileCode == null ? null : current.populations().get(profileCode);
        String profileSource = profile == null ? null : profile.sourceCode();

        return queries.stream()
                .map(query -> query.bedcaFoodId() == null || query.foodName() == null
                        ? MeasureChoiceDto.NONE
                        : matcher.chooseMeasure(current.measures(), own, query,
                        categoriser.of(query.foodName()), profileSource))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ReferenceFoodMeasure> measureEntities(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return measureRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ReferenceFoodMeasure::getId, Function.identity()));
    }

    @Override
    public FoodMeasureDto describe(ReferenceFoodMeasure measure) {
        return measure == null ? null : mapper.toDto(measure);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodMeasureDto> dietMeasures(Long dietId) {
        return measureRepository.findByDietIdOrderByIdAsc(dietId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public FoodMeasureDto saveDietMeasure(Long dietId, DietMeasureRequestDto request) {
        if ((request.grams() == null) == (request.ml() == null)) {
            throw new InvalidReferenceException(
                    "A measure weighs in grams or in millilitres: send exactly one of grams and ml");
        }
        BedcaFood food = bedcaFoodService.entityById(request.bedcaFoodId());
        ReferenceFoodMeasure measure = measureRepository.findByDietIdOrderByIdAsc(dietId).stream()
                .filter(row -> row.getMeasure() == request.measure()
                        && Objects.equals(row.getBedcaFoodId(), food.getId())
                        && row.getSize() == request.size())
                .findFirst()
                .orElseGet(ReferenceFoodMeasure::new);

        measure.setDietId(dietId);
        measure.setSource(null);
        measure.setCode(null);
        measure.setMeasure(request.measure());
        measure.setSize(request.size());
        measure.setCount(BigDecimal.ONE);
        measure.setBedcaFoodId(food.getId());
        measure.setFoodCategory(categoriser.of(food.getName()));
        measure.setKeywords(null);
        measure.setFoodLabel(Texts.truncate(food.getName(), LABEL_MAX));
        measure.setGramsMin(request.grams());
        measure.setGramsMax(request.grams());
        measure.setMlMin(request.ml());
        measure.setMlMax(request.ml());
        measure.setState(FoodState.UNSPECIFIED);
        measure.setWeightBasis(WeightBasis.NET_EDIBLE);
        measure.setGrossGrams(null);
        measure.setHouseholdText("1 " + request.measure().label());
        measure.setPageRef(null);
        measure.setNote(Texts.clean(request.note(), NOTE_MAX));
        return mapper.toDto(measureRepository.save(measure));
    }

    @Override
    @Transactional
    public void deleteDietMeasure(Long dietId, Long measureId) {
        ReferenceFoodMeasure measure = measureRepository.findById(measureId)
                .filter(row -> dietId.equals(row.getDietId()))
                .orElseThrow(() -> ReferenceNotFoundException.measure(measureId));
        measureRepository.delete(measure);
    }

    @Override
    @Transactional
    public Map<Long, Long> copyDietMeasures(Long fromDietId, Long toDietId) {
        Map<Long, Long> copied = new HashMap<>();
        for (ReferenceFoodMeasure row : measureRepository.findByDietIdOrderByIdAsc(fromDietId)) {
            ReferenceFoodMeasure copy = new ReferenceFoodMeasure();
            copy.setDietId(toDietId);
            copy.setMeasure(row.getMeasure());
            copy.setSize(row.getSize());
            copy.setCount(row.getCount());
            copy.setBedcaFoodId(row.getBedcaFoodId());
            copy.setFoodCategory(row.getFoodCategory());
            copy.setKeywords(row.getKeywords());
            copy.setFoodLabel(row.getFoodLabel());
            copy.setGramsMin(row.getGramsMin());
            copy.setGramsMax(row.getGramsMax());
            copy.setMlMin(row.getMlMin());
            copy.setMlMax(row.getMlMax());
            copy.setState(row.getState());
            copy.setWeightBasis(row.getWeightBasis());
            copy.setGrossGrams(row.getGrossGrams());
            copy.setHouseholdText(row.getHouseholdText());
            copy.setNote(row.getNote());
            copied.put(row.getId(), measureRepository.save(copy).getId());
        }
        return copied;
    }

    /**
     * One pass per table, in the order the rows point at each other. A code
     * already stored is written over and keeps its id, so a re-sync brings
     * corrections in and every diet still pointing at a measure keeps pointing
     * at it. A row naming a composition-database food that is not loaded is
     * skipped and said so — the composition database may simply not be synced
     * yet — while a row naming a source or population that does not exist is a
     * mistake in the files, and nothing is stored.
     */
    @Override
    @Transactional
    public ReferenceSyncSummaryDto store(ReferenceRowsDto rows) {
        List<TableSync> tables = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        Map<String, ReferenceSource> sources = byCode(sourceRepository.findAll(), ReferenceSource::getCode);
        tables.add(upsert("ref_sources", rows.sources(), sources, ReferenceRowsDto.Source::code,
                ReferenceSource::new, (entity, row) -> mapper.update(entity, row),
                sourceRepository::saveAll));

        Map<String, ReferencePopulation> populations =
                byCode(populationRepository.findAll(), ReferencePopulation::getCode);
        tables.add(upsert("ref_populations", rows.populations(), populations,
                ReferenceRowsDto.Population::code, ReferencePopulation::new,
                (entity, row) -> mapper.update(entity, row,
                        required(sources, row.sourceCode(), "source", row.origin())),
                populationRepository::saveAll));

        Set<Long> foods = knownFoods(rows);

        Map<String, ReferenceRation> rations = byCode(rationRepository.findAll(), ReferenceRation::getCode);
        List<ReferenceRowsDto.Ration> rationRows = rows.rations().stream()
                .filter(row -> foodKnown(row.bedcaFoodId(), foods, row.origin(), row.code(), skipped))
                .toList();
        tables.add(upsert("ref_rations", rationRows, rations, ReferenceRowsDto.Ration::code,
                ReferenceRation::new,
                (entity, row) -> mapper.update(entity, row,
                        required(populations, row.populationCode(), "population", row.origin())),
                rationRepository::saveAll));

        Map<String, ReferenceFoodMeasure> measures = byCode(
                measureRepository.findByDietIdIsNullOrderByIdAsc(), ReferenceFoodMeasure::getCode);
        List<ReferenceRowsDto.FoodMeasure> measureRows = rows.foodMeasures().stream()
                .filter(row -> foodKnown(row.bedcaFoodId(), foods, row.origin(), row.code(), skipped))
                .toList();
        tables.add(upsert("ref_food_measures", measureRows, measures,
                ReferenceRowsDto.FoodMeasure::code, ReferenceFoodMeasure::new,
                (entity, row) -> mapper.update(entity, row,
                        required(sources, row.sourceCode(), "source", row.origin())),
                measureRepository::saveAll));

        Map<String, ReferenceRecommendation> recommendations =
                byCode(recommendationRepository.findAll(), ReferenceRecommendation::getCode);
        tables.add(upsert("ref_recommendations", rows.recommendations(), recommendations,
                ReferenceRowsDto.Recommendation::code, ReferenceRecommendation::new,
                (entity, row) -> mapper.update(entity, row,
                        required(populations, row.populationCode(), "population", row.origin())),
                recommendationRepository::saveAll));

        Map<String, ReferenceMealShare> shares =
                byCode(mealShareRepository.findAll(), ReferenceMealShare::getCode);
        tables.add(upsert("ref_meal_shares", rows.mealShares(), shares,
                ReferenceRowsDto.MealShare::code, ReferenceMealShare::new,
                (entity, row) -> mapper.update(entity, row,
                        required(populations, row.populationCode(), "population", row.origin())),
                mealShareRepository::saveAll));

        Map<String, ReferenceExchangeSystem> exchanges =
                byCode(exchangeSystemRepository.findAll(), ReferenceExchangeSystem::getCode);
        tables.add(upsert("ref_exchange_systems", rows.exchangeSystems(), exchanges,
                ReferenceRowsDto.ExchangeSystem::code, ReferenceExchangeSystem::new,
                (entity, row) -> mapper.update(entity, row,
                        required(sources, row.sourceCode(), "source", row.origin())),
                exchangeSystemRepository::saveAll));

        requireBorrowedSharesExist(rows);
        snapshot = null;

        List<String> attributions = rows.sources().stream()
                .map(ReferenceRowsDto.Source::attribution)
                .toList();
        return new ReferenceSyncSummaryDto(tables, skipped, attributions);
    }

    private PopulationView population(String code) {
        PopulationView view = code == null ? null : snapshot().populations().get(code);
        if (view == null) {
            throw ReferenceNotFoundException.profile(code);
        }
        return view;
    }

    private Set<Long> knownFoods(ReferenceRowsDto rows) {
        Set<Long> wanted = new HashSet<>();
        rows.rations().stream().map(ReferenceRowsDto.Ration::bedcaFoodId)
                .filter(Objects::nonNull).forEach(wanted::add);
        rows.foodMeasures().stream().map(ReferenceRowsDto.FoodMeasure::bedcaFoodId)
                .filter(Objects::nonNull).forEach(wanted::add);
        return bedcaFoodService.entitiesByIds(wanted).keySet();
    }

    private static boolean foodKnown(Long bedcaFoodId, Set<Long> known, String origin, String code,
                                     List<String> skipped) {
        if (bedcaFoodId == null || known.contains(bedcaFoodId)) {
            return true;
        }
        skipped.add(origin + " " + code + ": composition-database food " + bedcaFoodId
                + " is not loaded (run POST /api/bedca/sync, then sync again)");
        return false;
    }

    private void requireBorrowedSharesExist(ReferenceRowsDto rows) {
        Set<String> withShares = rows.mealShares().stream()
                .map(ReferenceRowsDto.MealShare::populationCode)
                .collect(Collectors.toSet());
        for (ReferenceRowsDto.Population population : rows.populations()) {
            String from = population.mealSharesFrom();
            if (from != null && !withShares.contains(from)) {
                throw new InvalidReferenceException(population.origin() + " " + population.code()
                        + " borrows meal shares from " + from + ", which has none");
            }
        }
    }

    private static <E> Map<String, E> byCode(Collection<E> entities, Function<E, String> codeOf) {
        Map<String, E> map = new LinkedHashMap<>();
        for (E entity : entities) {
            String code = codeOf.apply(entity);
            if (code != null) {
                map.put(code, entity);
            }
        }
        return map;
    }

    private static <E> E required(Map<String, E> byCode, String code, String what, String origin) {
        E found = code == null ? null : byCode.get(code);
        if (found == null) {
            throw new InvalidReferenceException(origin + ": no " + what + " with code " + code);
        }
        return found;
    }

    private static <E, R> TableSync upsert(String table, List<R> rows, Map<String, E> stored,
                                           Function<R, String> codeOf,
                                           Supplier<E> create,
                                           BiConsumer<E, R> apply,
                                           Function<List<E>, List<E>> saveAll) {
        List<E> changed = new ArrayList<>(rows.size());
        int inserted = 0;
        int updated = 0;
        Set<String> seen = new HashSet<>();
        for (R row : rows) {
            String code = codeOf.apply(row);
            if (!seen.add(code)) {
                throw new InvalidReferenceException(table + ": code " + code + " appears twice");
            }
            E entity = stored.get(code);
            if (entity == null) {
                entity = create.get();
                inserted++;
            } else {
                updated++;
            }
            apply.accept(entity, row);
            changed.add(entity);
        }
        List<E> saved = saveAll.apply(changed);
        for (int at = 0; at < saved.size(); at++) {
            stored.put(codeOf.apply(rows.get(at)), saved.get(at));
        }
        return new TableSync(table, rows.size(), inserted, updated);
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            current = load();
            snapshot = current;
        }
        return current;
    }

    private Snapshot load() {
        List<ReferenceSourceDto> sources = sourceRepository.findAllByOrderByTierAscYearDesc().stream()
                .map(mapper::toDto)
                .toList();

        Map<String, PopulationView> populations = new LinkedHashMap<>();
        for (ReferencePopulation population : populationRepository.findAllByOrderByIdAsc()) {
            ReferenceSource source = population.getSource();
            populations.put(population.getCode(), new PopulationView(
                    population.getCode(), population.getLabel(), source.getCode(),
                    source.getShortName(), source.getTier(), source.getYear(),
                    population.getAgeMinMonths(), population.getAgeMaxMonths(),
                    population.getContext(), population.getMealSharesFrom(),
                    population.getMealSharesNote(), population.isSelectable(), population.getId()));
        }

        List<RationDto> rations = rationRepository.findAllByOrderByIdAsc().stream()
                .map(mapper::toDto)
                .toList();
        List<FoodMeasureDto> measures = measureRepository.findByDietIdIsNullOrderByIdAsc().stream()
                .map(mapper::toDto)
                .toList();

        Map<String, List<RecommendationDto>> recommendations = new LinkedHashMap<>();
        for (ReferenceRecommendation row : recommendationRepository.findAllByOrderByIdAsc()) {
            recommendations.computeIfAbsent(row.getPopulation().getCode(), code -> new ArrayList<>())
                    .add(mapper.toDto(row));
        }

        Map<String, List<MealShareDto>> shares = new LinkedHashMap<>();
        Map<String, String> sharePages = new HashMap<>();
        for (ReferenceMealShare row : mealShareRepository.findAllByOrderByIdAsc()) {
            String code = row.getPopulation().getCode();
            shares.computeIfAbsent(code, c -> new ArrayList<>())
                    .add(new MealShareDto(row.getMealType(), row.getPctMin(), row.getPctMax(),
                            row.getNote()));
            sharePages.putIfAbsent(code, row.getPageRef());
        }

        List<ExchangeSystemDto> exchanges = exchangeSystemRepository.findAllByOrderByIdAsc().stream()
                .map(mapper::toDto)
                .toList();

        return new Snapshot(sources, populations, rations, measures, recommendations, shares,
                sharePages, exchanges);
    }

    private record Snapshot(
            List<ReferenceSourceDto> sources,
            Map<String, PopulationView> populations,
            List<RationDto> rations,
            List<FoodMeasureDto> measures,
            Map<String, List<RecommendationDto>> recommendations,
            Map<String, List<MealShareDto>> shares,
            Map<String, String> sharePages,
            List<ExchangeSystemDto> exchanges) {
    }

    private record PopulationView(
            String code, String label, String sourceCode, String sourceShortName, int sourceTier,
            Integer sourceYear, Integer ageMinMonths, Integer ageMaxMonths, String context,
            String mealSharesFrom, String mealSharesNote, boolean selectable, Long id) {

        /** Nearest tier first, then the newest document, then the order it was loaded in. */
        static final Comparator<PopulationView> ORDER = Comparator
                .comparingInt(PopulationView::sourceTier)
                .thenComparing(view -> view.sourceYear() == null ? 0 : -view.sourceYear())
                .thenComparing(PopulationView::id);

        boolean covers(int ageMonths) {
            if (ageMinMonths == null && ageMaxMonths == null) {
                return false;
            }
            return (ageMinMonths == null || ageMonths >= ageMinMonths)
                    && (ageMaxMonths == null || ageMonths <= ageMaxMonths);
        }

        ReferenceProfileDto profile(boolean suggested) {
            return new ReferenceProfileDto(code, label, sourceCode, sourceShortName, ageMinMonths,
                    ageMaxMonths, context, selectable, suggested);
        }
    }
}
