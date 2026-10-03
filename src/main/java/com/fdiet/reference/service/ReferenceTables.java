package com.fdiet.reference.service;

import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.service.ICompositionFoodService;
import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MealShareDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.RecommendationDto;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto.TableSync;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto;
import com.fdiet.reference.dto.YieldFactorDto;
import com.fdiet.reference.exception.InvalidReferenceException;
import com.fdiet.reference.mapper.IReferenceMapper;
import com.fdiet.reference.model.ReferenceExchangeSystem;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.model.ReferenceMealShare;
import com.fdiet.reference.model.ReferencePopulation;
import com.fdiet.reference.model.ReferenceRation;
import com.fdiet.reference.model.ReferenceRecommendation;
import com.fdiet.reference.model.ReferenceSource;
import com.fdiet.reference.model.ReferenceYieldFactor;
import com.fdiet.reference.repository.ReferenceExchangeSystemRepository;
import com.fdiet.reference.repository.ReferenceFoodMeasureRepository;
import com.fdiet.reference.repository.ReferenceMealShareRepository;
import com.fdiet.reference.repository.ReferencePopulationRepository;
import com.fdiet.reference.repository.ReferenceRationRepository;
import com.fdiet.reference.repository.ReferenceRecommendationRepository;
import com.fdiet.reference.repository.ReferenceSourceRepository;
import com.fdiet.reference.repository.ReferenceYieldFactorRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The repository half of {@link ReferenceService}: it reads the published
 * reference tables whole into a snapshot, hands measure rows out as entities,
 * and stores a sync's rows. It is part of the owning service, split off by job
 * rather than by table — only {@code ReferenceService} calls it, and that
 * service holds the snapshot and drops it once a sync has written.
 */
@Component
class ReferenceTables {

    private final ReferenceSourceRepository sourceRepository;
    private final ReferencePopulationRepository populationRepository;
    private final ReferenceRationRepository rationRepository;
    private final ReferenceFoodMeasureRepository measureRepository;
    private final ReferenceRecommendationRepository recommendationRepository;
    private final ReferenceMealShareRepository mealShareRepository;
    private final ReferenceExchangeSystemRepository exchangeSystemRepository;
    private final ReferenceYieldFactorRepository yieldFactorRepository;
    private final IReferenceMapper mapper;
    private final ICompositionFoodService compositionFoodService;

    ReferenceTables(ReferenceSourceRepository sourceRepository,
                        ReferencePopulationRepository populationRepository,
                        ReferenceRationRepository rationRepository,
                        ReferenceFoodMeasureRepository measureRepository,
                        ReferenceRecommendationRepository recommendationRepository,
                        ReferenceMealShareRepository mealShareRepository,
                        ReferenceExchangeSystemRepository exchangeSystemRepository,
                        ReferenceYieldFactorRepository yieldFactorRepository,
                        IReferenceMapper mapper,
                        ICompositionFoodService compositionFoodService) {
        this.sourceRepository = sourceRepository;
        this.populationRepository = populationRepository;
        this.rationRepository = rationRepository;
        this.measureRepository = measureRepository;
        this.recommendationRepository = recommendationRepository;
        this.mealShareRepository = mealShareRepository;
        this.exchangeSystemRepository = exchangeSystemRepository;
        this.yieldFactorRepository = yieldFactorRepository;
        this.mapper = mapper;
        this.compositionFoodService = compositionFoodService;
    }

    /**
     * Every published row, read once into transport records: one query per
     * table, a few hundred rows in all.
     */
    ReferenceSnapshot load() {
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
        List<FoodMeasureDto> measures = measureRepository
                .findByDietIdIsNullAndGlobalCriterionFalseOrderByIdAsc().stream()
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

        List<YieldFactorDto> yields = yieldFactorRepository.findAllByOrderByIdAsc().stream()
                .map(mapper::toDto)
                .toList();

        return new ReferenceSnapshot(sources, populations, rations, measures, recommendations,
                shares, sharePages, exchanges, yields);
    }

    /** Measure rows as managed entities, keyed by id. One {@code findAllById}. */
    Map<Long, ReferenceFoodMeasure> measureEntities(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return measureRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ReferenceFoodMeasure::getId, Function.identity()));
    }

    /**
     * One pass per table, in the order the rows point at each other. A code
     * already stored is written over and keeps its id, so a re-sync brings
     * corrections in and every diet still pointing at a measure keeps pointing
     * at it. A row naming a composition food that is not loaded is skipped and
     * said so — the composition tables may simply not be synced yet — while a
     * row naming a source or population that does not exist is a mistake in the
     * files, and nothing is stored.
     *
     * <p>The composition foods are resolved in one batched lookup, whatever the
     * number of rows naming one: O(n) in the rows, one query per table.
     */
    ReferenceSyncSummaryDto store(ReferenceRowsDto rows) {
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

        Map<CompositionKey, Long> foods = compositionFoodService.idsByKey(Stream.concat(
                        rows.rations().stream().map(ReferenceRowsDto.Ration::compositionFood),
                        rows.foodMeasures().stream().map(ReferenceRowsDto.FoodMeasure::compositionFood))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        Map<String, ReferenceRation> rations = byCode(rationRepository.findAll(), ReferenceRation::getCode);
        List<ReferenceRowsDto.Ration> rationRows = rows.rations().stream()
                .filter(row -> foodKnown(row.compositionFood(), foods, row.origin(), row.code(), skipped))
                .toList();
        tables.add(upsert("ref_rations", rationRows, rations, ReferenceRowsDto.Ration::code,
                ReferenceRation::new,
                (entity, row) -> mapper.update(entity, row,
                        required(populations, row.populationCode(), "population", row.origin()),
                        foodIdOf(row.compositionFood(), foods)),
                rationRepository::saveAll));

        Map<String, ReferenceFoodMeasure> measures = byCode(
                measureRepository.findByDietIdIsNullAndGlobalCriterionFalseOrderByIdAsc(),
                ReferenceFoodMeasure::getCode);
        List<ReferenceRowsDto.FoodMeasure> measureRows = rows.foodMeasures().stream()
                .filter(row -> foodKnown(row.compositionFood(), foods, row.origin(), row.code(), skipped))
                .toList();
        tables.add(upsert("ref_food_measures", measureRows, measures,
                ReferenceRowsDto.FoodMeasure::code, ReferenceFoodMeasure::new,
                (entity, row) -> mapper.update(entity, row,
                        required(sources, row.sourceCode(), "source", row.origin()),
                        foodIdOf(row.compositionFood(), foods)),
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

        Map<String, ReferenceYieldFactor> yields =
                byCode(yieldFactorRepository.findAll(), ReferenceYieldFactor::getCode);
        tables.add(upsert("ref_yield_factors", rows.yieldFactors(), yields,
                ReferenceRowsDto.YieldFactor::code, ReferenceYieldFactor::new,
                (entity, row) -> mapper.update(entity, row,
                        required(sources, row.sourceCode(), "source", row.origin())),
                yieldFactorRepository::saveAll));

        requireBorrowedSharesExist(rows);

        List<String> attributions = rows.sources().stream()
                .map(ReferenceRowsDto.Source::attribution)
                .toList();
        return new ReferenceSyncSummaryDto(tables, skipped, attributions);
    }

    private static boolean foodKnown(CompositionKey food, Map<CompositionKey, Long> known, String origin,
                                     String code, List<String> skipped) {
        if (food == null || known.containsKey(food)) {
            return true;
        }
        skipped.add(origin + " " + code + ": composition food " + food
                + " is not loaded (run POST /api/composition/sync, then sync again)");
        return false;
    }

    private static Long foodIdOf(CompositionKey food, Map<CompositionKey, Long> known) {
        return food == null ? null : known.get(food);
    }

    private static void requireBorrowedSharesExist(ReferenceRowsDto rows) {
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
}
