package com.fdiet.diet.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.diet.domain.Diet;
import com.fdiet.diet.dto.ComposeRequestDto;
import com.fdiet.diet.dto.ComposedFragmentDto;
import com.fdiet.diet.dto.CopyDietRequestDto;
import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietMeasureSavedDto;
import com.fdiet.diet.dto.DietRationsDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.DietSettingsDto;
import com.fdiet.diet.dto.DietSummaryDto;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.dto.ParseDishRequestDto;
import com.fdiet.diet.dto.ResolveIngredientDto;
import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.helpers.IMealTextParser;
import com.fdiet.diet.helpers.IPortionScaler;
import com.fdiet.diet.mapper.IDietMapper;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.DietStatus;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.PlannedIngredient;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.diet.repository.DietRepository;
import com.fdiet.diet.repository.PlannedDishRepository;
import com.fdiet.diet.repository.PlannedIngredientRepository;
import com.fdiet.food.dto.FoodSuggestionDto;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.patient.model.Patient;
import com.fdiet.patient.service.IPatientService;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.dto.DietMeasureRequestDto;
import com.fdiet.reference.dto.FoodMeasureDto;
import com.fdiet.reference.dto.MeasureChoiceDto;
import com.fdiet.reference.dto.MeasureQueryDto;
import com.fdiet.reference.model.ReferenceFoodMeasure;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class DietService implements IDietService {

    private static final Sort BY_ID = Sort.by(Sort.Direction.ASC, "id");

    /** How many more candidates are ranked than shown, so a state disagreement can sink. */
    private static final int SUGGESTION_POOL = 3;

    private static final int NAME_MAX = 255;

    private final DietRepository dietRepository;
    private final PlannedIngredientRepository ingredientRepository;
    private final PlannedDishRepository dishRepository;
    private final IDietMapper dietMapper;
    private final IMealTextParser mealTextParser;
    private final IFoodResolverService foodResolverService;
    private final IFoodItemService foodItemService;
    private final IBedcaFoodService bedcaFoodService;
    private final IPatientService patientService;
    private final IReferenceService referenceService;
    private final IPortionScaler portionScaler;
    private final IDietRationService rationService;
    private final int suggestionLimit;

    public DietService(DietRepository dietRepository,
                       PlannedIngredientRepository ingredientRepository,
                       PlannedDishRepository dishRepository,
                       IDietMapper dietMapper,
                       IMealTextParser mealTextParser,
                       IFoodResolverService foodResolverService,
                       IFoodItemService foodItemService,
                       IBedcaFoodService bedcaFoodService,
                       IPatientService patientService,
                       IReferenceService referenceService,
                       IPortionScaler portionScaler,
                       IDietRationService rationService,
                       @Value("${fdiet.diet.suggestion-limit:5}") int suggestionLimit) {
        this.dietRepository = dietRepository;
        this.ingredientRepository = ingredientRepository;
        this.dishRepository = dishRepository;
        this.dietMapper = dietMapper;
        this.mealTextParser = mealTextParser;
        this.foodResolverService = foodResolverService;
        this.foodItemService = foodItemService;
        this.bedcaFoodService = bedcaFoodService;
        this.patientService = patientService;
        this.referenceService = referenceService;
        this.portionScaler = portionScaler;
        this.rationService = rationService;
        this.suggestionLimit = suggestionLimit;
    }

    /**
     * A new week takes the profile it names, or — when it names none — the one
     * suggested for the patient's age: the adult profile for an adult or for a
     * patient with no birth date, a school band for a child, none below the
     * youngest band any loaded source covers. The suggestion is stored as the
     * choice, visible and changeable, never re-applied behind anybody's back.
     */
    @Override
    @Transactional
    public DietDto create(DietRequestDto request) {
        List<DietDay> week = validated(request);
        Foods foods = foodsOf(week);

        // Through the owning service, so a patient id nothing carries comes back
        // as that module's 404 rather than as a foreign key violation.
        Patient patient = patientService.entityById(request.patientId());
        // Null asks for the profile the patient's age suggests; a blank is a
        // person saying "none".
        String profile = request.referenceProfileCode() == null
                ? referenceService.suggestedProfileCode(patient.ageInMonths(LocalDate.now()))
                : request.referenceProfileCode().isBlank()
                        ? null
                        : requireProfile(request.referenceProfileCode());
        Measures measures = measuresOf(week, foods, null, profile);
        archiveActive(patient.getId());

        DietPlan plan = new DietPlan();
        plan.setPatient(patient);
        plan.setName(request.name());
        plan.setStatus(DietStatus.ACTIVE);
        plan.setStartedOn(request.startedOn());
        plan.setReferenceProfileCode(profile);
        plan.setClinical(Boolean.TRUE.equals(request.clinical()));
        fill(plan, week, foods, measures);

        return dietMapper.toDto(dietRepository.save(plan));
    }

    /**
     * The week is replaced wholesale. The old meals are deleted in their own
     * flush before the new ones are written: Hibernate orders its inserts ahead
     * of its deletes, and {@code uk_diet_meals_slot} would reject the new
     * Monday breakfast while the old one is still there.
     */
    @Override
    @Transactional
    public DietDto update(Long id, DietRequestDto request) {
        List<DietDay> week = validated(request);
        Foods foods = foodsOf(week);

        DietPlan plan = dietRepository.findWithMealsById(id)
                .orElseThrow(() -> DietNotFoundException.diet(id));
        requireSamePatient(plan, request.patientId());
        plan.setName(request.name());
        plan.setStartedOn(request.startedOn());
        if (request.referenceProfileCode() != null) {
            plan.setReferenceProfileCode(request.referenceProfileCode().isBlank()
                    ? null
                    : requireProfile(request.referenceProfileCode()));
        }
        if (request.clinical() != null) {
            plan.setClinical(request.clinical());
        }
        Measures measures = measuresOf(week, foods, plan.getId(), plan.getReferenceProfileCode());

        plan.getMeals().clear();
        dietRepository.saveAndFlush(plan);

        fill(plan, week, foods, measures);
        return dietMapper.toDto(dietRepository.save(plan));
    }

    @Override
    @Transactional
    public DietDto updateSettings(Long id, DietSettingsDto settings) {
        DietPlan plan = dietRepository.findWithMealsById(id)
                .orElseThrow(() -> DietNotFoundException.diet(id));
        String name = Texts.clean(settings.name(), NAME_MAX);
        if (name != null) {
            plan.setName(name);
        }
        if (settings.referenceProfileCode() != null) {
            // Null leaves the profile alone; a blank takes it off, so the week
            // is read against nothing.
            plan.setReferenceProfileCode(settings.referenceProfileCode().isBlank()
                    ? null
                    : requireProfile(settings.referenceProfileCode()));
        }
        if (settings.clinical() != null) {
            plan.setClinical(settings.clinical());
        }
        return dietMapper.toDto(dietRepository.save(plan));
    }

    /**
     * The same week, written a second time for somebody else.
     *
     * <p>The rows are built afresh rather than shared: the two diets are edited
     * and republished independently from here on, and a shared meal would make
     * one nutritionist's edit land in another patient's week. What does carry
     * over is every food match already made — that is most of the work in an
     * imported diet, and re-matching by name would throw away the ones a person
     * decided by hand — and the household measures that weigh the ingredients,
     * including the source diet's own criteria, which are written again for the
     * copy so the two can be changed apart.
     *
     * <p>The journal does not come with it. What one patient thought of a plate,
     * and what they ate beside it, is their own record and hangs off their own
     * diet id.
     */
    @Override
    @Transactional
    public DietDto copy(Long id, CopyDietRequestDto request) {
        DietPlan source = dietRepository.findWithMealsById(id)
                .orElseThrow(() -> DietNotFoundException.diet(id));
        Patient target = patientService.entityById(request.patientId());

        // Frees the target's active slot — which may be the source's own, when a
        // week is being copied forward for the same patient.
        archiveActive(target.getId());

        DietPlan copy = new DietPlan();
        copy.setPatient(target);
        copy.setName(request.name() == null ? source.getName() : request.name());
        copy.setStatus(DietStatus.ACTIVE);
        copy.setStartedOn(request.startedOn() == null ? LocalDate.now() : request.startedOn());
        copy.setReferenceProfileCode(source.getReferenceProfileCode());
        copy.setClinical(source.isClinical());
        boolean ownMeasures = false;
        for (PlannedMeal meal : source.getMeals()) {
            PlannedMeal copiedMeal =
                    new PlannedMeal(meal.getDayOfWeek(), meal.getType(), meal.getName());
            copy.addMeal(copiedMeal);
            for (PlannedDish dish : meal.getDishes()) {
                PlannedDish copiedDish = new PlannedDish(dish.getName(), dish.getRawText());
                copiedMeal.addDish(copiedDish);
                for (PlannedIngredient ingredient : dish.getIngredients()) {
                    PlannedIngredient copied = new PlannedIngredient(
                            ingredient.getRawName(),
                            ingredient.getFoodItem(),
                            ingredient.getBedcaFood(),
                            ingredient.getQuantity(),
                            ingredient.getUnit());
                    copied.setState(ingredient.getState());
                    copied.setSize(ingredient.getSize());
                    copied.setFoodMeasure(ingredient.getFoodMeasure());
                    ownMeasures |= ingredient.getFoodMeasure() != null
                            && ingredient.getFoodMeasure().isDietOwn();
                    copiedDish.addIngredient(copied);
                }
            }
        }
        DietPlan saved = dietRepository.save(copy);
        if (ownMeasures) {
            repointOwnMeasures(source.getId(), saved);
            saved = dietRepository.save(saved);
        }
        return dietMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DietDto findActive(Long patientId) {
        requirePatient(patientId);
        return dietRepository.findFirstByPatientIdAndStatus(patientId, DietStatus.ACTIVE)
                .map(dietMapper::toDto)
                .orElseThrow(() -> DietNotFoundException.noActiveDiet(patientId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DietSummaryDto> current() {
        return dietRepository.findByStatusOrderByPatientNameAsc(DietStatus.ACTIVE).stream()
                .map(dietMapper::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DietDto findById(Long id) {
        return dietRepository.findWithMealsById(id)
                .map(dietMapper::toDto)
                .orElseThrow(() -> DietNotFoundException.diet(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<DietSummaryDto> history(Long patientId, int page, int size) {
        requirePatient(patientId);
        Pageable pageable = PageRequest.of(page, size);
        Page<DietPlan> archived = dietRepository.findByPatientIdAndStatusNotOrderByStartedOnDesc(
                patientId, DietStatus.ACTIVE, pageable);
        return PageDto.of(archived, dietMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long dietId) {
        return dietId != null && dietRepository.existsById(dietId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDishAt(Long dietId, DayOfWeek day, MealType mealType, int dishIndex) {
        if (dietId == null || day == null || mealType == null || dishIndex < 0) {
            return false;
        }
        return dishRepository.existsAtSlot(dietId, day, mealType, dishIndex);
    }

    /**
     * The candidates are asked for per ingredient but cost no query: the
     * composition database is small enough to rank in memory, and only the page
     * being looked at is ranked.
     */
    @Override
    @Transactional(readOnly = true)
    public PageDto<DishIngredient> ingredients(
            Long dietId, Boolean resolved, boolean suggest, int page, int size) {
        requireDiet(dietId);
        Pageable pageable = PageRequest.of(page, size, BY_ID);
        Page<PlannedIngredient> found;
        if (resolved == null) {
            found = ingredientRepository.findByDishMealDietId(dietId, pageable);
        } else if (resolved) {
            found = ingredientRepository.findMatched(dietId, pageable);
        } else {
            found = ingredientRepository.findUnmatched(dietId, pageable);
        }
        return PageDto.of(found, ingredient -> withSuggestions(ingredient, suggest));
    }

    @Override
    @Transactional
    public DishIngredient resolveIngredient(
            Long dietId, Long ingredientId, ResolveIngredientDto change) {
        if (change.foodItemId() != null && change.bedcaFoodId() != null) {
            throw new InvalidDietException(
                    "An ingredient points at one food: send foodItemId or bedcaFoodId, not both");
        }
        PlannedIngredient ingredient = ingredientRepository
                .findByIdAndDishMealDietId(ingredientId, dietId)
                .orElseThrow(() -> DietNotFoundException.ingredient(ingredientId));

        // Matching to one half of the catalogue releases the other, so the row
        // never carries two foods at once.
        if (change.bedcaFoodId() != null) {
            ingredient.setBedcaFood(bedcaFoodService.entityById(change.bedcaFoodId()));
            ingredient.setFoodItem(null);
        }
        if (change.foodItemId() != null) {
            ingredient.setFoodItem(foodItemService.entityById(change.foodItemId()));
            ingredient.setBedcaFood(null);
        }
        if (change.name() != null) {
            ingredient.setRawName(change.name());
        }
        if (change.quantity() != null) {
            ingredient.setQuantity(change.quantity());
        }
        if (change.unit() != null) {
            ingredient.setUnit(change.unit());
        }

        String profile = ingredient.getDish().getMeal().getDiet().getReferenceProfileCode();
        Long preferred = change.foodMeasureId() != null
                ? change.foodMeasureId()
                : ingredient.getFoodMeasure() == null ? null : ingredient.getFoodMeasure().getId();
        MeasureChoiceDto choice = choose(ingredient.getBedcaFood(), ingredient.getUnit(),
                ingredient, preferred, dietId, profile);
        if (change.foodMeasureId() != null
                && (choice.chosen() == null || !change.foodMeasureId().equals(choice.chosen().id()))) {
            throw new InvalidDietException("Household measure " + change.foodMeasureId()
                    + " does not weigh " + ingredient.getUnit() + " of this food. "
                    + "GET /api/reference/measures lists the ones that do");
        }
        ingredient.setFoodMeasure(entityOf(choice.chosen()));
        return dietMapper.toDto(ingredientRepository.save(ingredient));
    }

    /**
     * One written cell, read the way the workbook import reads it. Nothing is
     * stored: the ingredients are mapped through transient entities so the
     * editor is handed the same shape — matched name, scaled figures, the
     * measure that weighs it — that a stored ingredient comes back as, without
     * a row existing for it.
     */
    @Override
    @Transactional(readOnly = true)
    public Dish parse(ParseDishRequestDto request) {
        Dish written = mealTextParser.parse(request.text(), request.slotName());
        if (written == null) {
            throw new InvalidDietException("The cell is blank; there is no dish to read");
        }
        return resolved(written, request.dietId());
    }

    /**
     * The composer writes text; it does not store an ingredient of its own. The
     * fragment is built from the food's own catalogue name, so the parser matches
     * it exactly, and from a spelling of the measure the parser reads — then it
     * is read back through that same parser, and what comes back is what the
     * cell will hold.
     */
    @Override
    @Transactional(readOnly = true)
    public ComposedFragmentDto compose(ComposeRequestDto request) {
        if ((request.grams() == null) == (request.foodMeasureId() == null)) {
            throw new InvalidDietException(
                    "A composed food is a weight or a household measure: send grams or foodMeasureId");
        }
        BedcaFood food = bedcaFoodService.entityById(request.bedcaFoodId());
        String name = food.getName().replaceAll("[()+:]", " ").replaceAll("\\s+", " ").trim();

        String fragment;
        if (request.grams() != null) {
            fragment = name + " (" + amount(request.grams()) + " g" + stateWords(request.state()) + ")";
        } else {
            BigDecimal count = request.count() == null ? BigDecimal.ONE : request.count();
            FoodMeasureDto measure = referenceService.measureEntities(List.of(request.foodMeasureId()))
                    .values().stream().findFirst()
                    .map(referenceService::describe)
                    .orElseThrow(() -> new InvalidDietException(
                            "No household measure with id " + request.foodMeasureId()));
            boolean several = count.compareTo(BigDecimal.ONE) > 0;
            fragment = name + " (" + amount(count) + " "
                    + measure.measure().written(several, measure.size())
                    + stateWords(request.state()) + ")";
            MeasureChoiceDto choice = choose(food, measure.measure().label(), null,
                    measure.id(), request.dietId(), profileOf(request.dietId()));
            if (choice.chosen() == null || !choice.chosen().id().equals(measure.id())) {
                throw new InvalidDietException("Household measure " + measure.id()
                        + " does not weigh " + food.getName());
            }
        }

        Dish dish = resolved(Objects.requireNonNull(mealTextParser.parse(fragment, name)),
                request.dietId(), request.foodMeasureId());
        return new ComposedFragmentDto(fragment, dish.ingredients().get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public DietRationsDto rations(Long dietId, String profileCode) {
        DietPlan plan = dietRepository.findWithMealsById(dietId)
                .orElseThrow(() -> DietNotFoundException.diet(dietId));
        if (profileCode != null) {
            requireProfile(profileCode);
        }
        return rationService.account(plan, profileCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodMeasureDto> measures(Long dietId) {
        requireDiet(dietId);
        return referenceService.dietMeasures(dietId);
    }

    /**
     * Writes the diet's own weight for a measure, then attaches it to every
     * stored ingredient of the diet it can weigh — through the same rule a
     * publish would, so what the week holds now is what it will hold after the
     * next one.
     */
    @Override
    @Transactional
    public DietMeasureSavedDto saveMeasure(Long dietId, DietMeasureRequestDto request) {
        DietPlan plan = dietRepository.findById(dietId)
                .orElseThrow(() -> DietNotFoundException.diet(dietId));
        FoodMeasureDto saved = referenceService.saveDietMeasure(dietId, request);

        List<PlannedIngredient> candidates = ingredientRepository
                .findByDishMealDietId(dietId, Pageable.unpaged()).getContent().stream()
                .filter(ingredient -> ingredient.getBedcaFood() != null
                        && ingredient.getBedcaFood().getId().equals(request.bedcaFoodId())
                        && HouseholdMeasure.ofUnit(ingredient.getUnit())
                        .filter(measure -> measure == request.measure()).isPresent())
                .toList();
        List<MeasureChoiceDto> choices = referenceService.chooseMeasures(candidates.stream()
                .map(ingredient -> query(ingredient.getBedcaFood(), ingredient.getUnit(),
                        ingredient, null))
                .toList(), dietId, plan.getReferenceProfileCode());
        Map<Long, ReferenceFoodMeasure> entities = referenceService.measureEntities(choices.stream()
                .map(MeasureChoiceDto::chosen).filter(Objects::nonNull).map(FoodMeasureDto::id)
                .toList());

        int attached = 0;
        for (int at = 0; at < candidates.size(); at++) {
            FoodMeasureDto chosen = choices.get(at).chosen();
            if (chosen != null && chosen.dietOwn()) {
                candidates.get(at).setFoodMeasure(entities.get(chosen.id()));
                attached++;
            }
        }
        ingredientRepository.saveAll(candidates);
        return new DietMeasureSavedDto(saved, attached);
    }

    @Override
    @Transactional
    public void deleteMeasure(Long dietId, Long measureId) {
        requireDiet(dietId);
        referenceService.deleteDietMeasure(dietId, measureId);
    }

    private Dish resolved(Dish written, Long dietId) {
        return resolved(written, dietId, null);
    }

    /** A dish read from text, matched, weighed and mapped through transient rows. */
    private Dish resolved(Dish written, Long dietId, Long preferredMeasure) {
        List<DietDay> week = List.of(new DietDay(DayOfWeek.MONDAY, List.of(new MealDto(
                MealType.BREAKFAST, written.name(), List.of(written)))));
        Foods foods = foodsOf(week);
        String profile = profileOf(dietId);
        List<DishIngredient> ingredients = written.ingredients();
        if (preferredMeasure != null && ingredients.size() == 1) {
            ingredients = List.of(withMeasure(ingredients.get(0), preferredMeasure));
        }
        Dish preferred = written.withIngredients(ingredients);
        Measures measures = measuresOf(List.of(new DietDay(DayOfWeek.MONDAY, List.of(new MealDto(
                MealType.BREAKFAST, written.name(), List.of(preferred))))), foods, dietId, profile);

        List<DishIngredient> read = ingredients.stream()
                .map(ingredient -> dietMapper.toDto(dietMapper.toEntity(
                        ingredient, foods.of(ingredient), measures.of(ingredient))))
                .toList();
        return written.withIngredients(read);
    }

    private static DishIngredient withMeasure(DishIngredient ingredient, Long measureId) {
        return new DishIngredient(ingredient.id(), ingredient.name(), ingredient.quantity(),
                ingredient.unit(), ingredient.state(), ingredient.size(), ingredient.foodItemId(),
                ingredient.bedcaFoodId(), measureId, ingredient.matchedName(), ingredient.measure(),
                ingredient.stateMismatch(), ingredient.nutrition(), ingredient.suggestions());
    }

    private String profileOf(Long dietId) {
        if (dietId == null) {
            return null;
        }
        return dietRepository.findById(dietId)
                .orElseThrow(() -> DietNotFoundException.diet(dietId))
                .getReferenceProfileCode();
    }

    /**
     * The composition database's best candidates, with any whose name states the
     * other side of raw/cooked from the text moved to the end: {@code lentejas
     * cocidas} is offered {@code Lenteja, hervida} before {@code Lenteja, seca,
     * cruda}. Still only an order — nothing is matched by it.
     */
    private DishIngredient withSuggestions(PlannedIngredient ingredient, boolean suggest) {
        DishIngredient dto = dietMapper.toDto(ingredient);
        if (!suggest || dto.resolved()) {
            return dto;
        }
        List<FoodSuggestionDto> ranked =
                bedcaFoodService.suggest(dto.name(), suggestionLimit * SUGGESTION_POOL);
        FoodState written = ingredient.getState();
        List<FoodSuggestionDto> ordered = new ArrayList<>(ranked);
        ordered.sort(Comparator.comparing((FoodSuggestionDto suggestion) ->
                FoodState.disagree(written, FoodState.ofFoodName(suggestion.name()))));
        return dto.withSuggestions(ordered.stream().limit(suggestionLimit).toList());
    }

    /** Runs the week through the in-memory rules and hands it back ordered. */
    private List<DietDay> validated(DietRequestDto request) {
        return new Diet(request.days()).days();
    }

    private void requireDiet(Long dietId) {
        if (!dietRepository.existsById(dietId)) {
            throw DietNotFoundException.diet(dietId);
        }
    }

    private String requireProfile(String code) {
        if (!referenceService.profileExists(code)) {
            throw new InvalidDietException("No reference profile with code " + code
                    + ". GET /api/reference/profiles lists the ones a diet can be written against");
        }
        return code;
    }

    /** An unknown patient is that module's 404, not an empty listing. */
    private void requirePatient(Long patientId) {
        patientService.entityById(patientId);
    }

    /**
     * A diet does not change hands through an edit. Moving one would take the
     * patient's journal with it — the scores and the off-plan entries hang off
     * the diet id — so the week would arrive under a new name carrying somebody
     * else's opinion of it. {@code copy} is the answer, and it leaves both.
     */
    private static void requireSamePatient(DietPlan plan, Long patientId) {
        if (!plan.getPatient().getId().equals(patientId)) {
            throw new InvalidDietException("Diet " + plan.getId() + " belongs to patient "
                    + plan.getPatient().getId() + " and cannot be moved to patient " + patientId
                    + " by replacing its week. Copy it instead: POST /api/diets/"
                    + plan.getId() + "/copy");
        }
    }

    /**
     * Frees the patient's active slot, which {@code uk_diets_active} allows only
     * one of their rows to hold. Flushed on its own so the UPDATE reaches the
     * database before the INSERT of the diet replacing it.
     */
    private void archiveActive(Long patientId) {
        Optional<DietPlan> active =
                dietRepository.findFirstByPatientIdAndStatus(patientId, DietStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        DietPlan plan = active.get();
        plan.setStatus(DietStatus.ARCHIVED);
        plan.setEndedOn(LocalDate.now());
        dietRepository.saveAndFlush(plan);
    }

    /** The copy's ingredients weighed by the source diet's own measures point at the copy's. */
    private void repointOwnMeasures(Long sourceDietId, DietPlan copy) {
        Map<Long, Long> copied = referenceService.copyDietMeasures(sourceDietId, copy.getId());
        Map<Long, ReferenceFoodMeasure> entities = referenceService.measureEntities(copied.values());
        for (PlannedMeal meal : copy.getMeals()) {
            for (PlannedDish dish : meal.getDishes()) {
                for (PlannedIngredient ingredient : dish.getIngredients()) {
                    ReferenceFoodMeasure measure = ingredient.getFoodMeasure();
                    if (measure != null && measure.isDietOwn()) {
                        Long replacement = copied.get(measure.getId());
                        ingredient.setFoodMeasure(replacement == null ? null : entities.get(replacement));
                    }
                }
            }
        }
    }

    private void fill(DietPlan plan, List<DietDay> week, Foods foods, Measures measures) {
        for (DietDay day : week) {
            for (MealDto meal : day.meals()) {
                PlannedMeal plannedMeal = dietMapper.toEntity(day.day(), meal);
                plan.addMeal(plannedMeal);
                for (Dish dish : meal.dishes()) {
                    PlannedDish plannedDish = dietMapper.toEntity(dish);
                    plannedMeal.addDish(plannedDish);
                    for (DishIngredient ingredient : dish.ingredients()) {
                        plannedDish.addIngredient(dietMapper.toEntity(
                                ingredient, foods.of(ingredient), measures.of(ingredient)));
                    }
                }
            }
        }
    }

    /**
     * Every food the week needs, fetched in a handful of batched calls rather
     * than one lookup per ingredient: the ones the caller named by id, and the
     * ones that have to be matched by name.
     */
    private Foods foodsOf(List<DietDay> week) {
        Set<Long> itemIds = new LinkedHashSet<>();
        Set<Long> bedcaIds = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (DishIngredient ingredient : ingredientsOf(week)) {
            if (ingredient.bedcaFoodId() != null) {
                bedcaIds.add(ingredient.bedcaFoodId());
            } else if (ingredient.foodItemId() != null) {
                itemIds.add(ingredient.foodItemId());
            } else {
                names.add(ingredient.name());
            }
        }

        Map<Long, FoodItem> items = foodItemService.entitiesByIds(itemIds);
        requireAllFound(itemIds, items.keySet(), "food items");
        Map<Long, BedcaFood> generic = bedcaFoodService.entitiesByIds(bedcaIds);
        requireAllFound(bedcaIds, generic.keySet(), "composition-database foods");

        return new Foods(items, generic, foodResolverService.resolve(names));
    }

    /**
     * The household measure each ingredient of the week is weighed by, in one
     * pass: one query for the diet's own measures, the published ones already in
     * memory, and one batched load of the rows chosen. A measure the request
     * carries is kept when it still fits the food and the unit; otherwise the
     * rule decides again, and "decides" means only when the choice is not a
     * judgement.
     */
    private Measures measuresOf(List<DietDay> week, Foods foods, Long dietId, String profile) {
        List<DishIngredient> written = new ArrayList<>();
        List<MeasureQueryDto> queries = new ArrayList<>();
        for (DishIngredient ingredient : ingredientsOf(week)) {
            FoodMatch match = foods.of(ingredient);
            BedcaFood food = match == null ? null : match.bedcaFood();
            if (food == null || portionScaler.weighsDirectly(ingredient.unit())
                    || HouseholdMeasure.ofUnit(ingredient.unit()).isEmpty()) {
                continue;
            }
            written.add(ingredient);
            queries.add(new MeasureQueryDto(food.getId(), food.getName(), ingredient.unit(),
                    ingredient.size(), ingredient.foodMeasureId()));
        }
        if (queries.isEmpty()) {
            return Measures.NONE;
        }
        List<MeasureChoiceDto> choices = referenceService.chooseMeasures(queries, dietId, profile);
        Map<Long, ReferenceFoodMeasure> entities = referenceService.measureEntities(choices.stream()
                .map(MeasureChoiceDto::chosen).filter(Objects::nonNull).map(FoodMeasureDto::id)
                .distinct().toList());

        Map<DishIngredient, ReferenceFoodMeasure> chosen = new HashMap<>();
        for (int at = 0; at < written.size(); at++) {
            FoodMeasureDto choice = choices.get(at).chosen();
            if (choice != null) {
                chosen.put(written.get(at), entities.get(choice.id()));
            }
        }
        return new Measures(chosen);
    }

    /** The measure one stored ingredient may be weighed by, keeping {@code preferred} when it fits. */
    private MeasureChoiceDto choose(BedcaFood food, String unit, PlannedIngredient ingredient,
                                    Long preferred, Long dietId, String profile) {
        if (food == null || portionScaler.weighsDirectly(unit)) {
            return MeasureChoiceDto.NONE;
        }
        return referenceService.chooseMeasures(
                List.of(query(food, unit, ingredient, preferred)), dietId, profile).get(0);
    }

    private static MeasureQueryDto query(BedcaFood food, String unit, PlannedIngredient ingredient,
                                         Long preferred) {
        return new MeasureQueryDto(food.getId(), food.getName(), unit,
                ingredient == null ? null : ingredient.getSize(), preferred);
    }

    private ReferenceFoodMeasure entityOf(FoodMeasureDto measure) {
        if (measure == null) {
            return null;
        }
        return referenceService.measureEntities(List.of(measure.id())).get(measure.id());
    }

    private static List<DishIngredient> ingredientsOf(List<DietDay> week) {
        List<DishIngredient> ingredients = new ArrayList<>();
        for (DietDay day : week) {
            for (MealDto meal : day.meals()) {
                for (Dish dish : meal.dishes()) {
                    ingredients.addAll(dish.ingredients());
                }
            }
        }
        return ingredients;
    }

    /** {@code 70}, {@code 0,5}, {@code 1/2}: a number the parser reads back as written. */
    private static String amount(BigDecimal value) {
        BigDecimal stripped = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (stripped.compareTo(new BigDecimal("0.5")) == 0) {
            return "1/2";
        }
        if (stripped.compareTo(new BigDecimal("0.25")) == 0) {
            return "1/4";
        }
        return stripped.toPlainString().replace('.', ',');
    }

    private static String stateWords(FoodState state) {
        if (state == null) {
            return "";
        }
        return switch (state) {
            case RAW -> " en crudo";
            case DRY -> " en seco";
            case COOKED -> " cocinado";
            case CANNED -> " en conserva";
            case DRAINED -> " escurrido";
            case UNSPECIFIED -> "";
        };
    }

    /** An id the caller made up is a mistake to report, not a food to guess at. */
    private static void requireAllFound(Set<Long> asked, Set<Long> found, String what) {
        List<Long> unknown = asked.stream().filter(id -> !found.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw new InvalidDietException("Unknown " + what + ": " + unknown);
        }
    }

    /**
     * The ways an ingredient finds its food: the id the caller gave, on either
     * half of the catalogue, or its name matched against both. None may find
     * one, and then the ingredient is stored unmatched.
     */
    private record Foods(Map<Long, FoodItem> items,
                         Map<Long, BedcaFood> generic,
                         Map<String, FoodMatch> byName) {

        FoodMatch of(DishIngredient ingredient) {
            if (ingredient.bedcaFoodId() != null) {
                return FoodMatch.of(generic.get(ingredient.bedcaFoodId()));
            }
            if (ingredient.foodItemId() != null) {
                return FoodMatch.of(items.get(ingredient.foodItemId()));
            }
            String key = Texts.normaliseName(ingredient.name());
            return key == null ? null : byName.get(key);
        }
    }

    /**
     * The measure chosen for each written ingredient. Two ingredients written
     * exactly alike ask the same question, so they share the answer.
     */
    private record Measures(Map<DishIngredient, ReferenceFoodMeasure> chosen) {

        static final Measures NONE = new Measures(Map.of());

        ReferenceFoodMeasure of(DishIngredient ingredient) {
            return chosen.get(ingredient);
        }
    }
}
