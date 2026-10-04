package com.fdiet.diet.service;

import com.fdiet.diet.domain.Serving;
import com.fdiet.diet.dto.DietRationsDto;
import com.fdiet.diet.dto.DietRationsDto.Coverage;
import com.fdiet.diet.dto.DietRationsDto.DayRations;
import com.fdiet.diet.dto.DietRationsDto.DishUnits;
import com.fdiet.diet.dto.DietRationsDto.ExchangeCount;
import com.fdiet.diet.dto.DietRationsDto.GroupCount;
import com.fdiet.diet.dto.DietRationsDto.MealEnergy;
import com.fdiet.diet.dto.DietRationsDto.MealUnits;
import com.fdiet.diet.dto.DietRationsDto.RecommendationCheck;
import com.fdiet.diet.dto.DietRationsDto.Status;
import com.fdiet.diet.dto.DietRationsDto.Uncounted;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.dto.NutritionSummaryDto;
import com.fdiet.diet.model.DietPlan;
import com.fdiet.diet.model.PlannedDish;
import com.fdiet.diet.model.RecipeIngredient;
import com.fdiet.diet.model.PlannedMeal;
import com.fdiet.food.dto.NutritionDto;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.RecommendationPeriod;
import com.fdiet.reference.dto.ExchangeSystemDto;
import com.fdiet.reference.dto.MealShareDto;
import com.fdiet.reference.dto.MealSharesDto;
import com.fdiet.reference.dto.RationDto;
import com.fdiet.reference.dto.RecommendationDto;
import com.fdiet.reference.dto.ReferenceProfileDto;
import com.fdiet.reference.dto.ReferenceSourceDto;
import com.fdiet.reference.service.IReferenceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts a week in rations.
 *
 * <p><strong>Nothing is converted.</strong> A ration is compared with the
 * weight the ingredient is priced from, in the state it was written or, when
 * the text said nothing, the state the matched food's name states. Cooked
 * lentils against a dry ration are not counted — they are named, with the
 * reason — because the grams of one are not the grams of the other and the yield
 * that would turn one into the other is a choice nobody has made. A ration
 * published as a gross weight is cut to its edible part by the matched food's
 * edible fraction before it is compared, since the ingredient is priced as what
 * is eaten.
 */
@Service
public class DietRationService implements IDietRationService {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String OUTSIDE_GROUPS = "Fuera de los grupos del perfil";

    private final IDietNutritionService nutritionService;
    private final IReferenceService referenceService;

    public DietRationService(IDietNutritionService nutritionService,
                             IReferenceService referenceService) {
        this.nutritionService = nutritionService;
        this.referenceService = referenceService;
    }

    @Override
    public DietRationsDto account(DietPlan plan, String profileCode) {
        String code = profileCode != null ? profileCode : plan.getReferenceProfileCode();
        ReferenceProfileDto profile = code == null ? null : referenceService.profileSummary(code);
        if (code != null && profile == null) {
            // The diet names a profile no loaded source carries any more.
            code = null;
        }

        List<RecommendationDto> recommendations =
                code == null ? List.of() : referenceService.recommendations(code);
        MealSharesDto shares = code == null ? null : referenceService.mealShares(code);
        List<ExchangeSystemDto> exchanges = referenceService.exchangeSystems(plan.isClinical());

        Map<DayOfWeek, List<PlannedMeal>> mealsByDay = new EnumMap<>(DayOfWeek.class);
        for (PlannedMeal meal : plan.getMeals()) {
            mealsByDay.computeIfAbsent(meal.getDayOfWeek(), day -> new ArrayList<>()).add(meal);
        }

        List<DayRations> days = new ArrayList<>();
        Map<String, BigDecimal[]> weekByGroup = new LinkedHashMap<>();
        Set<String> sourceCodes = new LinkedHashSet<>();
        for (Map.Entry<DayOfWeek, List<PlannedMeal>> day : mealsByDay.entrySet()) {
            DayRations counted = day(day.getKey(), day.getValue(), code, recommendations, shares,
                    exchanges, sourceCodes);
            for (GroupCount group : counted.groups()) {
                BigDecimal[] total = weekByGroup.computeIfAbsent(group.groupCode(),
                        g -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                total[0] = total[0].add(group.rationsMin());
                total[1] = total[1].add(group.rationsMax());
            }
            days.add(counted);
        }

        boolean weekPartial = days.stream().anyMatch(counted -> partial(counted.uncounted()));
        List<RecommendationCheck> weekly = recommendations.stream()
                .filter(rec -> rec.period() == RecommendationPeriod.PER_WEEK)
                .map(rec -> check(rec, weekByGroup, weekPartial))
                .toList();

        if (profile != null) {
            sourceCodes.add(profile.sourceCode());
        }
        if (shares != null) {
            sourceCodes.add(shares.sourceCode());
        }
        exchanges.forEach(system -> sourceCodes.add(system.sourceCode()));
        List<ReferenceSourceDto> sources = referenceService.sources().stream()
                .filter(source -> sourceCodes.contains(source.code()))
                .toList();

        DietRationsDto.Profile summary = profile == null ? null : new DietRationsDto.Profile(
                profile.code(), profile.label(), profile.sourceCode(), profile.sourceShortName(),
                profile.context());
        return new DietRationsDto(plan.getId(), summary, shares, exchanges, days, weekly,
                mealsByDay.size(), sources);
    }

    private DayRations day(DayOfWeek day, List<PlannedMeal> meals, String profileCode,
                           List<RecommendationDto> recommendations, MealSharesDto shares,
                           List<ExchangeSystemDto> exchanges, Set<String> sourceCodes) {
        Map<String, GroupTally> groups = new LinkedHashMap<>();
        List<Uncounted> uncounted = new ArrayList<>();
        int ingredients = 0;
        int counted = 0;
        int unmatched = 0;
        int unweighed = 0;
        int noRation = 0;
        int stateMismatch = 0;

        List<Serving> all = new ArrayList<>();
        Map<MealType, List<Serving>> byMeal = new EnumMap<>(MealType.class);
        Map<MealType, String> mealNames = new EnumMap<>(MealType.class);
        for (PlannedMeal meal : meals) {
            mealNames.put(meal.getType(), meal.getName());
            for (PlannedDish dish : meal.getDishes()) {
                List<Serving> served = Serving.of(dish);
                all.addAll(served);
                byMeal.computeIfAbsent(meal.getType(), type -> new ArrayList<>()).addAll(served);
            }
        }

        for (Serving serving : all) {
            RecipeIngredient ingredient = serving.ingredient();
            ingredients++;
            if (!ingredient.isMatched()) {
                unmatched++;
                uncounted.add(new Uncounted(ingredient.getRawName(), "Sin vincular a un alimento"));
                continue;
            }
            BigDecimal oneServing = nutritionService.edibleGrams(ingredient);
            if (oneServing == null) {
                unweighed++;
                uncounted.add(new Uncounted(ingredient.getRawName(), ingredient.isRange()
                        ? "Cantidad en intervalo, sin confirmar"
                        : "Sin peso"));
                continue;
            }
            CompositionFood food = ingredient.getCompositionFood();
            RationDto ration = food == null ? null
                    : referenceService.countingRation(profileCode, food.getId(), food.getNameEs());
            BigDecimal[] weight = ration == null ? null : ration.edibleWeight(food.getEdiblePortion());
            if (ration == null || weight == null) {
                noRation++;
                uncounted.add(new Uncounted(ingredient.getRawName(), ration == null
                        ? OUTSIDE_GROUPS
                        : "La ración no tiene un peso comparable"));
                continue;
            }
            FoodState written = ingredient.getState() != null
                    ? ingredient.getState()
                    : FoodState.ofFoodName(food.getNameEs());
            if (FoodState.disagree(written, ration.state())) {
                stateMismatch++;
                uncounted.add(new Uncounted(ingredient.getRawName(), "Pesado " + stateWord(written)
                        + " y la ración es " + stateWord(ration.state())));
                continue;
            }
            BigDecimal grams = oneServing.multiply(serving.servings());
            counted++;
            sourceCodes.add(ration.sourceCode());
            groups.computeIfAbsent(ration.groupCode(), g -> new GroupTally(ration.groupLabel()))
                    .add(grams, weight);
        }

        List<GroupCount> groupCounts = groups.entrySet().stream()
                .map(entry -> entry.getValue().toDto(entry.getKey()))
                .toList();
        Map<String, BigDecimal[]> dayByGroup = new LinkedHashMap<>();
        groupCounts.forEach(group -> dayByGroup.put(group.groupCode(),
                new BigDecimal[]{group.rationsMin(), group.rationsMax()}));
        List<RecommendationCheck> daily = recommendations.stream()
                .filter(rec -> rec.period() == RecommendationPeriod.PER_DAY)
                .map(rec -> check(rec, dayByGroup, partial(uncounted)))
                .toList();

        NutritionDto dayTotals = nutritionService.summarise(all).totals();
        List<MealEnergy> energy = new ArrayList<>();
        Map<MealType, NutritionDto> mealTotals = new EnumMap<>(MealType.class);
        for (Map.Entry<MealType, List<Serving>> meal : byMeal.entrySet()) {
            NutritionDto totals = nutritionService.summarise(meal.getValue()).totals();
            mealTotals.put(meal.getKey(), totals);
            MealShareDto target = shareOf(shares, meal.getKey());
            energy.add(new MealEnergy(meal.getKey(), mealNames.get(meal.getKey()),
                    totals.energyKcal(), pct(totals.energyKcal(), dayTotals.energyKcal()),
                    target == null ? null : target.pctMin(), target == null ? null : target.pctMax(),
                    totals.carbohydratesG()));
        }

        List<DishTotals> dishTotals = new ArrayList<>();
        for (PlannedMeal meal : meals) {
            List<PlannedDish> dishes = meal.getDishes();
            for (int index = 0; index < dishes.size(); index++) {
                NutritionSummaryDto summary = nutritionService.summarise(Serving.of(dishes.get(index)));
                dishTotals.add(new DishTotals(meal.getType(), index, dishes.get(index).getName(),
                        summary.totals(), summary.complete()));
            }
        }
        List<ExchangeCount> exchangeCounts = exchanges.stream()
                .map(system -> new ExchangeCount(system.code(), system.name(), system.gramsPerUnit(),
                        units(dayTotals, system),
                        mealTotals.entrySet().stream()
                                .map(meal -> new MealUnits(meal.getKey(), units(meal.getValue(), system)))
                                .toList(),
                        dishTotals.stream()
                                .map(dish -> new DishUnits(dish.mealType(), dish.index(), dish.name(),
                                        units(dish.totals(), system), dish.complete()))
                                .toList()))
                .toList();

        return new DayRations(day, groupCounts, daily, energy, exchangeCounts,
                new Coverage(ingredients, counted, unmatched, unweighed, noRation, stateMismatch),
                uncounted);
    }

    /**
     * A count against a recommendation, range against range. A group the period
     * holds none of counts as zero: no fish on a Tuesday is zero rations of fish.
     */
    private static RecommendationCheck check(RecommendationDto rec, Map<String, BigDecimal[]> counts,
                                             boolean partial) {
        BigDecimal low = BigDecimal.ZERO;
        BigDecimal high = BigDecimal.ZERO;
        for (String group : rec.groupCodes()) {
            BigDecimal[] count = counts.get(group);
            if (count != null) {
                low = low.add(count[0]);
                high = high.add(count[1]);
            }
        }
        low = low.setScale(SCALE, RoundingMode.HALF_UP);
        high = high.setScale(SCALE, RoundingMode.HALF_UP);

        Status status;
        if (rec.rationsMax() != null && low.compareTo(rec.rationsMax()) > 0) {
            status = Status.ABOVE;
        } else if (rec.rationsMin() != null && high.compareTo(rec.rationsMin()) < 0) {
            status = Status.BELOW;
        } else if ((rec.rationsMin() == null || low.compareTo(rec.rationsMin()) >= 0)
                && (rec.rationsMax() == null || high.compareTo(rec.rationsMax()) <= 0)) {
            status = Status.WITHIN;
        } else {
            status = Status.UNCERTAIN;
        }
        if (partial && (status == Status.BELOW
                || (status == Status.WITHIN && rec.rationsMax() != null))) {
            // What was not counted may be the rest of the group.
            status = Status.UNCERTAIN;
        }
        return new RecommendationCheck(rec.code(), rec.label(), rec.groupCodes(), rec.rationsMin(),
                rec.rationsMax(), rec.period(), low, high, status, rec.note(), rec.pageRef());
    }

    /**
     * Whether something left out of the count could still belong to a group. A
     * matched food no ration covers cannot; anything else might.
     */
    private static boolean partial(List<Uncounted> uncounted) {
        return uncounted.stream().anyMatch(item -> !OUTSIDE_GROUPS.equals(item.reason()));
    }

    private static MealShareDto shareOf(MealSharesDto shares, MealType type) {
        if (shares == null) {
            return null;
        }
        return shares.shares().stream()
                .filter(share -> share.mealType().equals(type.name()))
                .findFirst().orElse(null);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() <= 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    /** One dish's figures, read once and counted in every exchange system. */
    private record DishTotals(MealType mealType, int index, String name, NutritionDto totals,
                              boolean complete) {
    }

    private static BigDecimal units(NutritionDto totals, ExchangeSystemDto system) {
        BigDecimal grams = switch (system.nutrient()) {
            case CARBOHYDRATE -> totals.carbohydratesG();
            case PROTEIN -> totals.proteinG();
            case FAT -> totals.fatG();
        };
        if (grams == null || system.gramsPerUnit().signum() <= 0) {
            return null;
        }
        return grams.divide(system.gramsPerUnit(), 1, RoundingMode.HALF_UP);
    }

    private static String stateWord(FoodState state) {
        return switch (state) {
            case RAW -> "en crudo";
            case DRY -> "en seco";
            case COOKED -> "cocinado";
            case CANNED -> "en conserva";
            case DRAINED -> "escurrido";
            case UNSPECIFIED -> "sin estado";
        };
    }

    /** Grams of one group, and the ration counts they come to. */
    private static final class GroupTally {

        private final String label;
        private BigDecimal grams = BigDecimal.ZERO;
        private BigDecimal rationsMin = BigDecimal.ZERO;
        private BigDecimal rationsMax = BigDecimal.ZERO;
        private int ingredients;

        private GroupTally(String label) {
            this.label = label;
        }

        /** The heavier ration gives the smaller count: 70 g is 0,88 of 80 g and 1,17 of 60 g. */
        private void add(BigDecimal edibleGrams, BigDecimal[] rationWeight) {
            grams = grams.add(edibleGrams);
            rationsMin = rationsMin.add(edibleGrams.divide(rationWeight[1], 4, RoundingMode.HALF_UP));
            rationsMax = rationsMax.add(edibleGrams.divide(rationWeight[0], 4, RoundingMode.HALF_UP));
            ingredients++;
        }

        private GroupCount toDto(String code) {
            return new GroupCount(code, label, grams.setScale(SCALE, RoundingMode.HALF_UP),
                    rationsMin.setScale(SCALE, RoundingMode.HALF_UP),
                    rationsMax.setScale(SCALE, RoundingMode.HALF_UP), ingredients);
        }
    }
}
