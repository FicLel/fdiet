package com.fdiet.reference.service;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.common.helper.Numbers;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.helpers.DataReader;
import com.fdiet.reference.domain.ExchangeNutrient;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.LicenceClass;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.RationRole;
import com.fdiet.reference.domain.RecommendationPeriod;
import com.fdiet.reference.domain.WeightBasis;
import com.fdiet.reference.dto.ReferenceRowsDto;
import com.fdiet.reference.dto.ReferenceSyncSummaryDto;
import com.fdiet.reference.exception.InvalidReferenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Turns {@code reference-data/} into reference rows.
 *
 * <p>The directory holds {@code sources.csv} and {@code exchange_systems.csv}
 * at its root, and one folder per source holding that source's
 * {@code populations.csv}, {@code rations.csv}, {@code food_measures.csv},
 * {@code recommendations.csv}, {@code meal_shares.csv} and
 * {@code yield_factors.csv} — whichever it has.
 * One folder per source keeps each licence with its own figures: the 5 al día
 * folder is CC BY-SA and carries its own licence file.
 *
 * <p>Columns are found by header name. A value that does not read — an unknown
 * state, a letter where a number goes — stops the sync with the file and line,
 * and nothing is stored: a reference figure read wrongly is exactly the wrong
 * number this layer exists to prevent.
 */
@Service
public class ReferenceImportService implements IReferenceImportService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceImportService.class);

    private static final String SOURCES = "sources.csv";
    private static final String EXCHANGE_SYSTEMS = "exchange_systems.csv";
    private static final String POPULATIONS = "populations.csv";
    private static final String RATIONS = "rations.csv";
    private static final String FOOD_MEASURES = "food_measures.csv";
    private static final String RECOMMENDATIONS = "recommendations.csv";
    private static final String MEAL_SHARES = "meal_shares.csv";
    private static final String YIELD_FACTORS = "yield_factors.csv";

    private final DataReader dataReader;
    private final IReferenceService referenceService;
    private final Path root;

    public ReferenceImportService(DataReader dataReader,
                                  IReferenceService referenceService,
                                  @Value("${fdiet.reference.data-path:reference-data}") String dataPath) {
        this.dataReader = dataReader;
        this.referenceService = referenceService;
        this.root = Path.of(dataPath);
    }

    @Override
    @Transactional
    public ReferenceSyncSummaryDto sync() {
        if (!Files.isDirectory(root)) {
            throw new UncheckedIOException(new IOException(
                    "The reference data directory " + root.toAbsolutePath() + " does not exist"));
        }
        List<Path> folders = folders();

        ReferenceRowsDto rows = new ReferenceRowsDto(
                read(List.of(root.resolve(SOURCES)), this::source),
                read(each(folders, POPULATIONS), this::population),
                read(each(folders, RATIONS), this::ration),
                read(each(folders, FOOD_MEASURES), this::foodMeasure),
                read(each(folders, RECOMMENDATIONS), this::recommendation),
                read(each(folders, MEAL_SHARES), this::mealShare),
                read(List.of(root.resolve(EXCHANGE_SYSTEMS)), this::exchangeSystem),
                read(each(folders, YIELD_FACTORS), this::yieldFactor));

        ReferenceSyncSummaryDto summary = referenceService.store(rows);
        log.info("Synced reference data from {}: {}; {} rows skipped", root.toAbsolutePath(),
                summary.tables(), summary.skipped().size());
        summary.skipped().forEach(reason -> log.warn("Reference row skipped: {}", reason));
        return summary;
    }

    private List<Path> folders() {
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + root.toAbsolutePath(), e);
        }
    }

    private static List<Path> each(List<Path> folders, String file) {
        return folders.stream().map(folder -> folder.resolve(file)).filter(Files::isRegularFile).toList();
    }

    private <T> List<T> read(List<Path> files, Function<Row, T> toRow) {
        List<T> rows = new ArrayList<>();
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            List<List<String>> records = dataReader.read(file);
            if (records.isEmpty()) {
                continue;
            }
            Map<String, Integer> columns = new LinkedHashMap<>();
            List<String> header = records.get(0);
            for (int at = 0; at < header.size(); at++) {
                columns.putIfAbsent(header.get(at).trim(), at);
            }
            for (int line = 1; line < records.size(); line++) {
                List<String> record = records.get(line);
                if (record.stream().allMatch(cell -> cell.isBlank())) {
                    continue;
                }
                String origin = root.relativize(file).toString().replace('\\', '/') + ":" + (line + 1);
                rows.add(toRow.apply(new Row(origin, record, columns)));
            }
        }
        return rows;
    }

    private ReferenceRowsDto.Source source(Row row) {
        return new ReferenceRowsDto.Source(row.origin(), row.required("code"),
                row.required("short_name"), row.required("title"), row.required("institution"),
                row.required("country"), row.integer("tier", true), row.integer("year", false),
                row.text("url"), row.enumeration("licence_class", LicenceClass.class, true),
                row.required("licence"), row.required("attribution"), row.bool("clinical"),
                row.date("retrieved_on"), row.text("notes"));
    }

    private ReferenceRowsDto.Population population(Row row) {
        return new ReferenceRowsDto.Population(row.origin(), row.required("code"),
                row.required("source_code"), row.required("label"),
                row.integer("age_min_months", false), row.integer("age_max_months", false),
                row.text("context"), row.text("meal_shares_from"), row.text("meal_shares_note"),
                row.bool("selectable"));
    }

    private ReferenceRowsDto.Ration ration(Row row) {
        ReferenceRowsDto.Ration ration = new ReferenceRowsDto.Ration(row.origin(),
                row.required("code"), row.required("population_code"), row.required("group_code"),
                row.required("group_label"),
                row.enumeration("food_category", FoodCategory.class, false), row.text("keywords"),
                row.id("bedca_food_id"), row.text("food_label"),
                row.enumeration("role", RationRole.class, false),
                row.decimal("grams_min"), row.decimal("grams_max"), row.decimal("ml_min"),
                row.decimal("ml_max"), row.decimal("units_min"), row.decimal("units_max"),
                row.enumeration("state", FoodState.class, true),
                row.enumeration("weight_basis", WeightBasis.class, true),
                row.text("household_text"), row.decimal("gross_grams"), row.required("page_ref"),
                row.text("note"));
        if (ration.gramsMin() == null && ration.mlMin() == null && ration.unitsMin() == null) {
            throw row.invalid("a ration needs grams, millilitres or units");
        }
        if (ration.bedcaFoodId() == null && ration.foodCategory() == null) {
            throw row.invalid("a ration names a composition-database food or a food family");
        }
        return ration;
    }

    private ReferenceRowsDto.FoodMeasure foodMeasure(Row row) {
        BigDecimal count = row.decimal("count");
        ReferenceRowsDto.FoodMeasure measure = new ReferenceRowsDto.FoodMeasure(row.origin(),
                row.required("code"), row.required("source_code"),
                row.enumeration("measure", HouseholdMeasure.class, true),
                row.enumeration("size", PortionSize.class, false),
                count == null ? BigDecimal.ONE : count, row.id("bedca_food_id"),
                row.enumeration("food_category", FoodCategory.class, false), row.text("keywords"),
                row.required("food_label"), row.decimal("grams_min"), row.decimal("grams_max"),
                row.decimal("ml_min"), row.decimal("ml_max"),
                row.enumeration("state", FoodState.class, true),
                row.enumeration("weight_basis", WeightBasis.class, true),
                row.decimal("gross_grams"), row.text("household_text"), row.required("page_ref"),
                row.text("note"));
        if (measure.gramsMin() == null && measure.mlMin() == null) {
            throw row.invalid("a measure needs grams or millilitres");
        }
        if (measure.bedcaFoodId() == null && measure.foodCategory() == null) {
            throw row.invalid("a measure names a composition-database food or a food family");
        }
        return measure;
    }

    private ReferenceRowsDto.Recommendation recommendation(Row row) {
        ReferenceRowsDto.Recommendation recommendation = new ReferenceRowsDto.Recommendation(
                row.origin(), row.required("code"), row.required("population_code"),
                row.required("label"), row.required("group_codes"), row.decimal("rations_min"),
                row.decimal("rations_max"),
                row.enumeration("period", RecommendationPeriod.class, true),
                row.required("page_ref"), row.text("note"));
        if (recommendation.rationsMin() == null && recommendation.rationsMax() == null) {
            throw row.invalid("a recommendation needs a minimum, a maximum or both");
        }
        return recommendation;
    }

    private ReferenceRowsDto.MealShare mealShare(Row row) {
        return new ReferenceRowsDto.MealShare(row.origin(), row.required("code"),
                row.required("population_code"), row.required("meal_type"),
                row.requiredDecimal("pct_min"), row.requiredDecimal("pct_max"),
                row.required("page_ref"), row.text("note"));
    }

    private ReferenceRowsDto.ExchangeSystem exchangeSystem(Row row) {
        return new ReferenceRowsDto.ExchangeSystem(row.origin(), row.required("code"),
                row.required("source_code"), row.required("name"),
                row.enumeration("nutrient", ExchangeNutrient.class, true),
                row.requiredDecimal("grams_per_unit"), row.bool("clinical"), row.text("note"));
    }

    private ReferenceRowsDto.YieldFactor yieldFactor(Row row) {
        BigDecimal yield = row.requiredDecimal("yield_pct");
        if (yield.signum() <= 0 || yield.compareTo(new BigDecimal("400")) > 0) {
            throw row.invalid("yield_pct " + yield + " is not a cooking yield");
        }
        return new ReferenceRowsDto.YieldFactor(row.origin(), row.required("code"),
                row.required("source_code"),
                row.enumeration("food_category", FoodCategory.class, true), row.text("keywords"),
                row.required("food_label"), row.required("method"), row.text("method_keywords"),
                yield, row.integer("samples", false), row.required("page_ref"), row.text("note"));
    }

    /** One CSV record, read by column name, with the file and line it came from. */
    private record Row(String origin, List<String> record, Map<String, Integer> columns) {

        String text(String column) {
            Integer at = columns.get(column);
            return at == null || at >= record.size() ? null : Texts.trimToNull(record.get(at));
        }

        String required(String column) {
            String value = text(column);
            if (value == null) {
                throw invalid(column + " is required");
            }
            return value;
        }

        BigDecimal decimal(String column) {
            String value = text(column);
            if (value == null) {
                return null;
            }
            BigDecimal number = Numbers.toDecimal(value);
            if (number == null) {
                throw invalid(column + " is not a number: " + value);
            }
            return number;
        }

        BigDecimal requiredDecimal(String column) {
            required(column);
            return decimal(column);
        }

        Integer integer(String column, boolean needed) {
            BigDecimal number = needed ? requiredDecimal(column) : decimal(column);
            return number == null ? null : number.intValueExact();
        }

        Long id(String column) {
            BigDecimal number = decimal(column);
            return number == null ? null : number.longValueExact();
        }

        boolean bool(String column) {
            String value = required(column).toLowerCase(Locale.ROOT);
            return switch (value) {
                case "true", "1", "si", "sí", "yes" -> true;
                case "false", "0", "no" -> false;
                default -> throw invalid(column + " is not true or false: " + value);
            };
        }

        LocalDate date(String column) {
            String value = required(column);
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                throw invalid(column + " is not a yyyy-MM-dd date: " + value);
            }
        }

        <E extends Enum<E>> E enumeration(String column, Class<E> type, boolean needed) {
            String value = needed ? required(column) : text(column);
            if (value == null) {
                return null;
            }
            try {
                return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw invalid(column + " is not a " + type.getSimpleName() + ": " + value);
            }
        }

        InvalidReferenceException invalid(String reason) {
            return new InvalidReferenceException("reference-data/" + origin + ": " + reason);
        }
    }
}
