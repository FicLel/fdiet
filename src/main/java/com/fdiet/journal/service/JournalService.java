package com.fdiet.journal.service;

import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.service.IDietService;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.model.FoodItem;
import com.fdiet.food.service.IBedcaFoodService;
import com.fdiet.food.service.IFoodItemService;
import com.fdiet.journal.dto.DayExtrasDto;
import com.fdiet.journal.dto.DietJournalDto;
import com.fdiet.journal.dto.DishScoreDto;
import com.fdiet.journal.dto.ExtraFoodDto;
import com.fdiet.journal.dto.LogExtraFoodRequestDto;
import com.fdiet.journal.dto.ScoreDishRequestDto;
import com.fdiet.journal.exception.InvalidJournalEntryException;
import com.fdiet.journal.exception.JournalEntryNotFoundException;
import com.fdiet.journal.mapper.IJournalMapper;
import com.fdiet.journal.model.DishScore;
import com.fdiet.journal.model.ExtraFood;
import com.fdiet.journal.repository.DishScoreRepository;
import com.fdiet.journal.repository.ExtraFoodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class JournalService implements IJournalService {

    /** One decimal is as fine as an average of five whole stars can honestly be. */
    private static final int AVERAGE_SCALE = 1;

    private final DishScoreRepository scoreRepository;
    private final ExtraFoodRepository extraRepository;
    private final IJournalMapper journalMapper;
    private final IJournalNutritionService journalNutritionService;
    private final IDietService dietService;
    private final IBedcaFoodService bedcaFoodService;
    private final IFoodItemService foodItemService;

    public JournalService(DishScoreRepository scoreRepository,
                          ExtraFoodRepository extraRepository,
                          IJournalMapper journalMapper,
                          IJournalNutritionService journalNutritionService,
                          IDietService dietService,
                          IBedcaFoodService bedcaFoodService,
                          IFoodItemService foodItemService) {
        this.scoreRepository = scoreRepository;
        this.extraRepository = extraRepository;
        this.journalMapper = journalMapper;
        this.journalNutritionService = journalNutritionService;
        this.dietService = dietService;
        this.bedcaFoodService = bedcaFoodService;
        this.foodItemService = foodItemService;
    }

    /**
     * Two queries for the week, whatever it holds. The screen draws all seven
     * days at once, so a call per day would be seven round trips for at most
     * seventy small rows.
     */
    @Override
    @Transactional(readOnly = true)
    public DietJournalDto find(Long dietId) {
        requireDiet(dietId);

        List<DishScoreDto> scores = scoreRepository.findByDietId(dietId).stream()
                .sorted(Comparator.comparing(DishScore::getDayOfWeek)
                        .thenComparing(DishScore::getMealType)
                        .thenComparingInt(DishScore::getDishIndex))
                .map(journalMapper::toDto)
                .toList();

        return new DietJournalDto(
                dietId,
                scores,
                byDay(extraRepository.findByDietIdOrderByLoggedAtAsc(dietId)),
                average(scores),
                scores.size());
    }

    @Override
    @Transactional
    public DishScoreDto score(Long dietId,
                              DayOfWeek day,
                              MealType mealType,
                              int dishIndex,
                              ScoreDishRequestDto request) {
        requireSlot(dietId, day, mealType, dishIndex);

        DishScore score = scoreRepository
                .findByDietIdAndDayOfWeekAndMealTypeAndDishIndex(dietId, day, mealType, dishIndex)
                .orElseGet(() -> new DishScore(dietId, day, mealType, dishIndex, request.score()));
        score.rescore(request.score());
        return journalMapper.toDto(scoreRepository.save(score));
    }

    @Override
    @Transactional
    public void clearScore(Long dietId, DayOfWeek day, MealType mealType, int dishIndex) {
        requireDiet(dietId);
        DishScore score = scoreRepository
                .findByDietIdAndDayOfWeekAndMealTypeAndDishIndex(dietId, day, mealType, dishIndex)
                .orElseThrow(() -> JournalEntryNotFoundException.score(
                        day + "/" + mealType + "/" + dishIndex));
        scoreRepository.delete(score);
    }

    @Override
    @Transactional
    public ExtraFoodDto logExtra(Long dietId, LogExtraFoodRequestDto request) {
        requireDiet(dietId);
        if (request.bedcaFoodId() != null && request.foodItemId() != null) {
            throw new InvalidJournalEntryException(
                    "An extra points at one food or at none: give bedcaFoodId or foodItemId, "
                            + "not both");
        }
        // Through the owning services, so an id nothing carries comes back as
        // that module's 404 rather than as a foreign key violation.
        BedcaFood bedca = request.bedcaFoodId() == null
                ? null
                : bedcaFoodService.entityById(request.bedcaFoodId());
        FoodItem item = request.foodItemId() == null
                ? null
                : foodItemService.entityById(request.foodItemId());

        ExtraFood extra = new ExtraFood(
                dietId,
                request.day(),
                request.name().trim(),
                request.quantity(),
                request.unit().trim(),
                bedca,
                item);
        return journalMapper.toDto(extraRepository.save(extra));
    }

    /**
     * The diet is part of the question, not decoration: an entry of one diet can
     * never be removed through the URL of another.
     */
    @Override
    @Transactional
    public void removeExtra(Long dietId, Long extraId) {
        requireDiet(dietId);
        ExtraFood extra = extraRepository.findWithFoodById(extraId)
                .filter(candidate -> dietId.equals(candidate.getDietId()))
                .orElseThrow(() -> JournalEntryNotFoundException.extra(extraId));
        extraRepository.delete(extra);
    }

    /** Every day that has an entry, in the order a week is read. */
    private List<DayExtrasDto> byDay(List<ExtraFood> extras) {
        Map<DayOfWeek, List<ExtraFood>> grouped = new EnumMap<>(DayOfWeek.class);
        for (ExtraFood extra : extras) {
            grouped.computeIfAbsent(extra.getDayOfWeek(), day -> new ArrayList<>()).add(extra);
        }
        List<DayExtrasDto> days = new ArrayList<>(grouped.size());
        for (Map.Entry<DayOfWeek, List<ExtraFood>> day : grouped.entrySet()) {
            days.add(new DayExtrasDto(
                    day.getKey(),
                    day.getValue().stream().map(journalMapper::toDto).toList(),
                    journalNutritionService.summarise(day.getValue())));
        }
        return days;
    }

    /** Null while nothing has been scored — not zero, which would read as a verdict. */
    private static BigDecimal average(List<DishScoreDto> scores) {
        if (scores.isEmpty()) {
            return null;
        }
        int sum = scores.stream().mapToInt(DishScoreDto::score).sum();
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(scores.size()), AVERAGE_SCALE, RoundingMode.HALF_UP);
    }

    private void requireDiet(Long dietId) {
        if (!dietService.exists(dietId)) {
            throw DietNotFoundException.diet(dietId);
        }
    }

    /**
     * A score is kept against the slot rather than the dish row, so nothing
     * about that row stops one being written for a plate the week does not
     * have. This is what does: one count query, asked of the service that owns
     * the dishes.
     */
    private void requireSlot(Long dietId, DayOfWeek day, MealType mealType, int dishIndex) {
        requireDiet(dietId);
        if (!dietService.hasDishAt(dietId, day, mealType, dishIndex)) {
            throw new InvalidJournalEntryException(
                    "The diet has no dish at " + day + "/" + mealType + "/" + dishIndex);
        }
    }
}
