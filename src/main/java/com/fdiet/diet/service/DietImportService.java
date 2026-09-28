package com.fdiet.diet.service;

import com.fdiet.common.helper.Texts;
import com.fdiet.diet.dto.DietDay;
import com.fdiet.diet.dto.DietDto;
import com.fdiet.diet.dto.DietImportSummaryDto;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.Dish;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.dto.MealDto;
import com.fdiet.diet.dto.MealType;
import com.fdiet.diet.dto.SheetGridDto;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.diet.helpers.IDietWorkbookReader;
import com.fdiet.diet.helpers.IMealTextParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the layout a diet is written in and hands the resulting week to
 * {@link IDietService}, the way {@code FoodImportService} hands its CSV rows to
 * the service that owns each table. It owns no repository of its own.
 *
 * <pre>
 *       A                B..H  (one column per day)
 *  1    (blank)          Lunes | Martes | … | Domingo
 *  2    Desayuno         a cell per day      -> BREAKFAST, one dish
 *  3    Media mañana     a cell per day      -> MORNING_SNACK, one dish
 *  4    Comida           == a row merged across the days ==
 *  5      Primer plato   a cell per day      -> LUNCH, first dish
 *  6      Segundo plato  a cell per day      -> LUNCH, second dish
 *  7      Postre         a cell per day      -> LUNCH, third dish
 *  …
 * </pre>
 *
 * <p>A row merged across the day columns names a meal whose dishes are the rows
 * under it; any other labelled row is a meal of a single dish. Neither the
 * number of days nor the number of rows is fixed — the header row decides the
 * columns, and the labels decide the rows.
 */
@Service
public class DietImportService implements IDietImportService {

    private static final Logger log = LoggerFactory.getLogger(DietImportService.class);

    private static final int LABEL_COLUMN = 0;
    private static final int HEADER_ROW = 0;
    private static final int NAME_MAX = 255;

    /** The day names of the header row, keyed the way {@link Texts#key} writes them. */
    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "LUNES", DayOfWeek.MONDAY,
            "MARTES", DayOfWeek.TUESDAY,
            "MIERCOLES", DayOfWeek.WEDNESDAY,
            "JUEVES", DayOfWeek.THURSDAY,
            "VIERNES", DayOfWeek.FRIDAY,
            "SABADO", DayOfWeek.SATURDAY,
            "DOMINGO", DayOfWeek.SUNDAY);

    /** The meal labels of the first column. Anything else is a dish of the meal above it. */
    private static final Map<String, MealType> MEALS = Map.of(
            "DESAYUNO", MealType.BREAKFAST,
            "MEDIA MANANA", MealType.MORNING_SNACK,
            "ALMUERZO", MealType.LUNCH,
            "COMIDA", MealType.LUNCH,
            "MERIENDA", MealType.AFTERNOON_SNACK,
            "CENA", MealType.DINNER);

    private final IDietWorkbookReader workbookReader;
    private final IMealTextParser mealTextParser;
    private final IDietService dietService;

    public DietImportService(IDietWorkbookReader workbookReader,
                             IMealTextParser mealTextParser,
                             IDietService dietService) {
        this.workbookReader = workbookReader;
        this.mealTextParser = mealTextParser;
        this.dietService = dietService;
    }

    @Override
    public DietImportSummaryDto importWorkbook(InputStream workbook, Long patientId, String sheet,
                                               String name, LocalDate startedOn,
                                               String referenceProfile, Boolean clinical) {
        SheetGridDto grid = workbookReader.read(workbook, sheet);
        List<DietDay> week = toWeek(grid);
        log.info("Parsed {} days from sheet '{}' for patient {}", week.size(), grid.name(),
                patientId);

        String dietName = Texts.clean(name == null ? grid.name() : name, NAME_MAX);
        DietDto stored = dietService.create(new DietRequestDto(
                patientId, dietName, startedOn == null ? LocalDate.now() : startedOn, week,
                // Blank passes through: it is somebody choosing no profile.
                referenceProfile == null ? null : referenceProfile.trim(), clinical));

        return summaryOf(stored, grid.name());
    }

    /** The sheet as days of meals, in the order the rows and columns run. */
    private List<DietDay> toWeek(SheetGridDto grid) {
        Map<Integer, DayOfWeek> days = daysOf(grid);
        if (days.isEmpty()) {
            throw new InvalidDietException(
                    "The sheet has no day names in its first row; expected Lunes … Domingo");
        }

        // day -> meal slot -> the dishes read for it, in row order.
        Map<DayOfWeek, Map<MealType, List<Dish>>> dishes = new LinkedHashMap<>();
        Map<MealType, String> mealNames = new EnumMap<>(MealType.class);

        MealType currentMeal = null;
        for (int row = HEADER_ROW + 1; row < grid.rows().size(); row++) {
            String label = grid.cell(row, LABEL_COLUMN);
            MealType labelled = MEALS.get(Texts.key(label));
            if (labelled != null) {
                currentMeal = labelled;
                mealNames.putIfAbsent(labelled, Texts.clean(label, NAME_MAX));
                if (isBanner(grid, row, days)) {
                    // The label is repeated across the day columns: it heads the
                    // rows below rather than holding a meal of its own.
                    continue;
                }
            }
            if (currentMeal == null) {
                // A row above the first meal label: a title, or a blank line.
                continue;
            }
            readRow(grid, row, days, label, currentMeal, dishes);
        }

        return dishes.entrySet().stream()
                .map(day -> new DietDay(day.getKey(), mealsOf(day.getValue(), mealNames)))
                .toList();
    }

    /** One row of cells, one dish per day that filled it in. */
    private void readRow(SheetGridDto grid, int row, Map<Integer, DayOfWeek> days, String label,
                         MealType meal, Map<DayOfWeek, Map<MealType, List<Dish>>> dishes) {
        String fallbackName = Texts.clean(label, NAME_MAX);
        days.forEach((column, day) -> {
            Dish dish = mealTextParser.parse(grid.cell(row, column), fallbackName);
            if (dish == null) {
                return;
            }
            dishes.computeIfAbsent(day, d -> new EnumMap<>(MealType.class))
                    .computeIfAbsent(meal, m -> new ArrayList<>())
                    .add(dish);
        });
    }

    private List<MealDto> mealsOf(Map<MealType, List<Dish>> byType, Map<MealType, String> names) {
        return byType.entrySet().stream()
                .map(entry -> new MealDto(
                        entry.getKey(),
                        names.getOrDefault(entry.getKey(), entry.getKey().name()),
                        entry.getValue()))
                .toList();
    }

    /** The day each column holds, read from the header row. */
    private Map<Integer, DayOfWeek> daysOf(SheetGridDto grid) {
        Map<Integer, DayOfWeek> days = new LinkedHashMap<>();
        List<String> header = grid.rows().isEmpty() ? List.of() : grid.rows().get(HEADER_ROW);
        for (int column = LABEL_COLUMN + 1; column < header.size(); column++) {
            DayOfWeek day = DAYS.get(Texts.key(header.get(column)));
            if (day != null && !days.containsValue(day)) {
                days.put(column, day);
            }
        }
        return days;
    }

    /**
     * A row merged across the day columns reads as its label in every one of
     * them, which is how {@code Comida} and {@code Cena} announce the rows
     * below instead of holding a meal themselves.
     */
    private boolean isBanner(SheetGridDto grid, int row, Map<Integer, DayOfWeek> days) {
        String label = grid.cell(row, LABEL_COLUMN);
        return days.keySet().stream().allMatch(column -> label.equals(grid.cell(row, column)));
    }

    private DietImportSummaryDto summaryOf(DietDto diet, String sheet) {
        int meals = 0;
        int dishes = 0;
        int ingredients = 0;
        int resolved = 0;
        for (DietDay day : diet.days()) {
            meals += day.meals().size();
            for (MealDto meal : day.meals()) {
                dishes += meal.dishes().size();
                for (Dish dish : meal.dishes()) {
                    ingredients += dish.ingredients().size();
                    for (DishIngredient ingredient : dish.ingredients()) {
                        if (ingredient.resolved()) {
                            resolved++;
                        }
                    }
                }
            }
        }
        return new DietImportSummaryDto(diet.id(), sheet, diet.name(), diet.days().size(),
                meals, dishes, ingredients, resolved, ingredients - resolved);
    }
}
