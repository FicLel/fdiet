package com.fdiet.reference.service;

import com.fdiet.alternative.helpers.IFoodCategoriser;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.MeasureUser;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureCriterionRequestDto;
import com.fdiet.reference.dto.MeasureUsageDto;
import com.fdiet.reference.exception.InvalidReferenceException;
import com.fdiet.reference.exception.ReferenceNotFoundException;
import com.fdiet.reference.mapper.IReferenceMapper;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.repository.ReferenceFoodMeasureRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns the nutritionist's rows of {@code ref_food_measures} — one diet's
 * criteria and her global ones — while {@code ReferenceService} owns the
 * published rows of the same table and the sync that writes them. Split by who
 * writes a row: a document or a person.
 *
 * <p>A global criterion is never written by the sync: the sync addresses rows by
 * code, and a criterion has none ({@code ck_ref_food_measures_global}).
 */
@Service
public class MeasureCriterionService implements IMeasureCriterionService {

    private static final int NOTE_MAX = 500;
    private static final int LABEL_MAX = 160;
    private static final String ONE = "1 ";

    private final ReferenceFoodMeasureRepository measureRepository;
    private final IReferenceMapper mapper;
    private final IFoodCategoriser categoriser;
    private final IBedcaFoodService bedcaFoodService;

    /**
     * Looked up when counted, not at construction: the counters are the recipe
     * and journal services, which depend on {@code IReferenceService}, which
     * depends on this — a cycle if they were injected eagerly.
     */
    private final ObjectProvider<IMeasureUsageCounter> counters;

    public MeasureCriterionService(ReferenceFoodMeasureRepository measureRepository,
                                   IReferenceMapper mapper,
                                   IFoodCategoriser categoriser,
                                   IBedcaFoodService bedcaFoodService,
                                   ObjectProvider<IMeasureUsageCounter> counters) {
        this.measureRepository = measureRepository;
        this.mapper = mapper;
        this.categoriser = categoriser;
        this.bedcaFoodService = bedcaFoodService;
        this.counters = counters;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodMeasureDto> dietRows(Long dietId) {
        return measureRepository.findByDietIdOrderByIdAsc(dietId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodMeasureDto> globalRows(Collection<Long> bedcaFoodIds) {
        if (bedcaFoodIds.isEmpty()) {
            return List.of();
        }
        return measureRepository.findByGlobalCriterionTrueAndBedcaFoodIdInOrderByIdAsc(bedcaFoodIds)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public FoodMeasureDto saveDietMeasure(Long dietId, MeasureCriterionRequestDto request) {
        requireOneWeight(request);
        BedcaFood food = bedcaFoodService.entityById(request.bedcaFoodId());
        ReferenceFoodMeasure measure = measureRepository.findByDietIdOrderByIdAsc(dietId).stream()
                .filter(row -> sameSlot(row, request))
                .findFirst()
                .orElseGet(ReferenceFoodMeasure::new);
        measure.setDietId(dietId);
        measure.setGlobalCriterion(false);
        write(measure, food, request);
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
        List<ReferenceFoodMeasure> rows = measureRepository.findByDietIdOrderByIdAsc(fromDietId);
        List<ReferenceFoodMeasure> copies = new ArrayList<>(rows.size());
        for (ReferenceFoodMeasure row : rows) {
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
            copies.add(copy);
        }
        // One batched write; saveAll answers in the order it was given.
        List<ReferenceFoodMeasure> saved = measureRepository.saveAll(copies);
        Map<Long, Long> copied = new HashMap<>();
        for (int at = 0; at < rows.size(); at++) {
            copied.put(rows.get(at).getId(), saved.get(at).getId());
        }
        return copied;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodMeasureDto> globalCriteria(Long bedcaFoodId) {
        if (bedcaFoodId != null) {
            return globalRows(List.of(bedcaFoodId));
        }
        return measureRepository.findByGlobalCriterionTrueOrderByFoodLabelAscIdAsc().stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FoodMeasureDto globalCriterion(Long id) {
        return mapper.toDto(globalEntity(id));
    }

    @Override
    @Transactional
    public FoodMeasureDto createGlobal(MeasureCriterionRequestDto request) {
        requireOneWeight(request);
        BedcaFood food = bedcaFoodService.entityById(request.bedcaFoodId());
        requireSlotFree(request, null);
        ReferenceFoodMeasure measure = new ReferenceFoodMeasure();
        measure.setDietId(null);
        measure.setGlobalCriterion(true);
        write(measure, food, request);
        return mapper.toDto(save(measure));
    }

    @Override
    @Transactional
    public FoodMeasureDto updateGlobal(Long id, MeasureCriterionRequestDto request) {
        requireOneWeight(request);
        ReferenceFoodMeasure measure = globalEntity(id);
        if (!sameSlot(measure, request)) {
            MeasureUsageDto usage = usage(id);
            if (usage.inUse()) {
                throw new InvalidReferenceException(describe(measure) + " " + reach(usage)
                        + ", so its food, measure and size stay as they are. Change the weight, "
                        + "or create a new criterion for the other food or measure");
            }
            requireSlotFree(request, id);
        }
        BedcaFood food = bedcaFoodService.entityById(request.bedcaFoodId());
        write(measure, food, request);
        return mapper.toDto(save(measure));
    }

    /**
     * The foreign keys from ingredients and extras set NULL on delete — right for
     * a diet's criterion, which goes with its diet — so here the check is a count:
     * deleting a criterion still in use would leave those rows silently unweighed.
     */
    @Override
    @Transactional
    public void deleteGlobal(Long id) {
        ReferenceFoodMeasure measure = globalEntity(id);
        MeasureUsageDto usage = usage(id);
        if (usage.inUse()) {
            throw new InvalidReferenceException(describe(measure) + " " + reach(usage)
                    + ". Write those quantities another way first, or change the criterion's "
                    + "weight instead of deleting it (GET /api/reference/criteria/" + id + "/usage)");
        }
        measureRepository.delete(measure);
    }

    /** One count query per counter — two — whatever the criterion weighs. */
    @Override
    @Transactional(readOnly = true)
    public MeasureUsageDto usage(Long id) {
        globalEntity(id);
        Map<MeasureUser, Long> counts = new EnumMap<>(MeasureUser.class);
        counters.orderedStream()
                .forEach(counter -> counts.merge(counter.user(), counter.countUsing(id), Long::sum));
        return new MeasureUsageDto(id, counts.getOrDefault(MeasureUser.RECIPE_INGREDIENT, 0L),
                counts.getOrDefault(MeasureUser.EXTRA_FOOD, 0L));
    }

    private ReferenceFoodMeasure globalEntity(Long id) {
        return measureRepository.findById(id)
                .filter(ReferenceFoodMeasure::isGlobalCriterion)
                .orElseThrow(() -> ReferenceNotFoundException.measure(id));
    }

    /** The unique index on {@code criterion_key} is underneath for the write this check races with. */
    private void requireSlotFree(MeasureCriterionRequestDto request, Long self) {
        measureRepository.findByGlobalCriterionTrueAndBedcaFoodIdInOrderByIdAsc(
                        List.of(request.bedcaFoodId())).stream()
                .filter(row -> sameSlot(row, request) && !row.getId().equals(self))
                .findFirst()
                .ifPresent(holder -> {
                    throw taken(holder.getId());
                });
    }

    private ReferenceFoodMeasure save(ReferenceFoodMeasure measure) {
        try {
            return measureRepository.saveAndFlush(measure);
        } catch (DataIntegrityViolationException e) {
            throw taken(null);
        }
    }

    private static InvalidReferenceException taken(Long holderId) {
        return new InvalidReferenceException("There is already a criterion for this food, measure "
                + "and size" + (holderId == null ? "" : " (id " + holderId + ")")
                + ". Change that one instead");
    }

    /** The row as a person filled it in: one measure, one point weight, the edible part. */
    private void write(ReferenceFoodMeasure measure, BedcaFood food, MeasureCriterionRequestDto request) {
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
        measure.setHouseholdText(ONE + request.measure().written(false, request.size()));
        measure.setPageRef(null);
        measure.setNote(Texts.clean(request.note(), NOTE_MAX));
    }

    private static void requireOneWeight(MeasureCriterionRequestDto request) {
        if ((request.grams() == null) == (request.ml() == null)) {
            throw new InvalidReferenceException(
                    "A measure weighs in grams or in millilitres: send exactly one of grams and ml");
        }
    }

    private static boolean sameSlot(ReferenceFoodMeasure row, MeasureCriterionRequestDto request) {
        return row.getMeasure() == request.measure()
                && Objects.equals(row.getBedcaFoodId(), request.bedcaFoodId())
                && row.getSize() == request.size();
    }

    private static String describe(ReferenceFoodMeasure measure) {
        return "The criterion \"" + measure.getHouseholdText() + "\" of " + measure.getFoodLabel();
    }

    private static String reach(MeasureUsageDto usage) {
        return "still weighs " + usage.ingredients() + " recipe ingredient(s) and "
                + usage.extraFoods() + " logged extra(s)";
    }
}
