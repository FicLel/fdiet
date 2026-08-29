package com.fdiet.food.service;

import com.fdiet.common.helper.Numbers;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.BedcaCsvRowDto;
import com.fdiet.food.dto.BedcaStoreResultDto;
import com.fdiet.food.dto.BedcaSyncSummaryDto;
import com.fdiet.food.dto.NutrientDto;
import com.fdiet.food.helpers.DataReader;
import com.fdiet.food.model.Nutrient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns bedca_foods.csv into rows of {@code bedca_foods}.
 *
 * <p>Columns are found by their header name, not by position: the file is 111
 * columns wide and only fourteen of them are wanted, so counting to them would
 * be both unreadable and fragile. A file missing a column simply leaves that
 * component empty.
 *
 * <p>Values are carried across untouched, each with the unit it was published
 * in — the terms of use forbid normalising them, and a kilojoule read as a
 * kilocalorie is a wrong diet. See BEDCA-ATTRIBUTION.txt.
 */
@Service
public class BedcaImportService implements IBedcaImportService {

    private static final Logger log = LoggerFactory.getLogger(BedcaImportService.class);

    // The metadata columns, by header name.
    private static final String ID = "f_id";
    private static final String NAME = "f_ori_name";
    private static final String ENGLISH_NAME = "f_eng_name";
    private static final String SCIENTIFIC_NAME = "sci_name";
    private static final String FOOD_GROUP = "namelevel1";
    private static final String FOOD_SUBGROUP = "namelevel2";
    private static final String ORIGIN = "f_origen";
    private static final String EDIBLE_PORTION = "edible_portion";

    /** The suffix the file gives a component's unit column: {@code ENERC_unit}. */
    private static final String UNIT_SUFFIX = "_unit";

    // Widths of the matching varchar columns.
    private static final int NAME_MAX = 255;
    private static final int UNIT_MAX = 16;
    private static final int ORIGIN_MAX = 16;

    private final DataReader dataReader;
    private final IBedcaFoodService bedcaFoodService;
    private final Path csv;

    public BedcaImportService(DataReader dataReader,
                              IBedcaFoodService bedcaFoodService,
                              @Value("${fdiet.bedca.csv-path:bedca_foods.csv}") String csvPath) {
        this.dataReader = dataReader;
        this.bedcaFoodService = bedcaFoodService;
        this.csv = Path.of(csvPath);
    }

    @Override
    @Transactional
    public BedcaSyncSummaryDto sync() {
        List<List<String>> records = dataReader.read(csv);
        if (records.isEmpty()) {
            return new BedcaSyncSummaryDto(0, 0, 0, 0, SOURCE);
        }

        Map<String, Integer> columns = columnsOf(records.get(0));
        List<List<String>> body = records.subList(1, records.size());

        List<BedcaCsvRowDto> rows = new ArrayList<>(body.size());
        int skipped = 0;
        for (List<String> record : body) {
            BedcaCsvRowDto row = toRow(record, columns);
            if (row == null) {
                skipped++;
                continue;
            }
            rows.add(row);
        }

        BedcaStoreResultDto stored = bedcaFoodService.storeAll(rows);
        log.info("Synced {} composition foods from {} ({} inserted, {} updated, {} skipped)",
                rows.size(), csv, stored.inserted(), stored.updated(), skipped);

        return new BedcaSyncSummaryDto(
                body.size(), skipped, stored.inserted(), stored.updated(), SOURCE);
    }

    /** Where each header name sits, so the row reader can ask for it by name. */
    private Map<String, Integer> columnsOf(List<String> header) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int at = 0; at < header.size(); at++) {
            String name = Texts.trimToNull(header.get(at));
            if (name != null) {
                columns.putIfAbsent(name, at);
            }
        }
        return columns;
    }

    /** Null for a row with no id or no name — there is nothing to key it on. */
    private BedcaCsvRowDto toRow(List<String> record, Map<String, Integer> columns) {
        Long id = Numbers.toLong(cell(record, columns, ID));
        String name = Texts.clean(cell(record, columns, NAME), NAME_MAX);
        if (id == null || name == null) {
            return null;
        }
        return new BedcaCsvRowDto(
                id,
                name,
                Texts.clean(cell(record, columns, ENGLISH_NAME), NAME_MAX),
                Texts.clean(cell(record, columns, SCIENTIFIC_NAME), NAME_MAX),
                Texts.clean(cell(record, columns, FOOD_GROUP), NAME_MAX),
                Texts.clean(cell(record, columns, FOOD_SUBGROUP), NAME_MAX),
                Texts.clean(cell(record, columns, ORIGIN), ORIGIN_MAX),
                Numbers.toDecimal(cell(record, columns, EDIBLE_PORTION)),
                nutrientsOf(record, columns));
    }

    /**
     * Every component fdiet keeps, when the file published one. A component
     * with no value is left out rather than stored as zero: the source not
     * measuring something is not the same as it measuring none.
     */
    private Map<String, NutrientDto> nutrientsOf(List<String> record, Map<String, Integer> columns) {
        Map<String, NutrientDto> nutrients = new LinkedHashMap<>();
        for (Nutrient nutrient : Nutrient.values()) {
            var value = Numbers.toDecimal(cell(record, columns, nutrient.code()));
            if (value == null) {
                continue;
            }
            String unit = Texts.clean(cell(record, columns, nutrient.code() + UNIT_SUFFIX), UNIT_MAX);
            nutrients.put(nutrient.key(), new NutrientDto(value, unit));
        }
        return nutrients;
    }

    private String cell(List<String> record, Map<String, Integer> columns, String column) {
        Integer at = columns.get(column);
        return at == null || at >= record.size() ? null : record.get(at);
    }
}
