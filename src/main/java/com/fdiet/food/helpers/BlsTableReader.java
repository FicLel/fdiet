package com.fdiet.food.helpers;

import com.fdiet.common.helper.Numbers;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionTableDto;
import com.fdiet.food.dto.NutrientDto;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.model.Nutrient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BLS 4.0 (Max Rubner-Institut): one sheet, 7,140 foods by 418 columns. Every
 * component has three columns — the value, its origin, its reference — and only
 * the value's header carries a unit: {@code ENERCC Energie (Kilokalorien) [kcal/100g]}.
 * A component is found by the EuroFIR code that starts that header.
 *
 * <p>Energy is {@code ENERCC} (kcal) and protein {@code PROT625} (nitrogen ×
 * 6.25), as BLS publishes them. Qualified cells ({@code TR}, {@code <LOD},
 * {@code <LOQ}, {@code -}) are left out.
 */
@Component
public class BlsTableReader implements ICompositionTableReader {

    static final String WORKBOOK = "BLS_4_0_Daten_2025_DE.xlsx";

    private static final String CODE = "BLS Code";
    private static final String NAME_DE = "Lebensmittelbezeichnung";
    private static final String NAME_EN = "Food name";

    private static final int NAME_MAX = 255;
    private static final int CODE_MAX = 32;

    /** How many leading characters of a BLS code name its food group ({@code G} vegetables). */
    private static final int GROUP_PREFIX = 1;

    /** {@code [kcal/100g]}: the unit is what sits between the bracket and the slash. */
    private static final Pattern UNIT = Pattern.compile("\\[([^/\\]]+)/100 ?g]");

    /** The EuroFIR code that starts each component's value header. */
    private static final Map<Nutrient, String> CODES = new EnumMap<>(Map.ofEntries(
            Map.entry(Nutrient.ENERGY, "ENERCC"),
            Map.entry(Nutrient.PROTEIN, "PROT625"),
            Map.entry(Nutrient.FAT, "FAT"),
            Map.entry(Nutrient.SATURATED_FAT, "FASAT"),
            Map.entry(Nutrient.CARBOHYDRATES, "CHO"),
            Map.entry(Nutrient.SUGARS, "SUGAR"),
            Map.entry(Nutrient.FIBER, "FIBT"),
            Map.entry(Nutrient.WATER, "WATER"),
            Map.entry(Nutrient.SODIUM, "NA"),
            Map.entry(Nutrient.POTASSIUM, "K"),
            Map.entry(Nutrient.CALCIUM, "CA"),
            Map.entry(Nutrient.IRON, "FE"),
            Map.entry(Nutrient.CHOLESTEROL, "CHORL"),
            Map.entry(Nutrient.VITAMIN_C, "VITC")));

    private final ISheetStreamReader sheetReader;

    public BlsTableReader(ISheetStreamReader sheetReader) {
        this.sheetReader = sheetReader;
    }

    @Override
    public CompositionSource source() {
        return CompositionSource.BLS;
    }

    /** One streamed pass over the sheet: O(n) in the 7,140 foods, one row in memory at a time. */
    @Override
    public CompositionTableDto read(Path folder) {
        List<CompositionFoodRowDto> rows = new ArrayList<>();
        int[] skipped = {0};
        Columns[] columns = {null};
        sheetReader.forEachRow(folder.resolve(WORKBOOK), record -> {
            if (columns[0] == null) {
                columns[0] = columnsOf(new SheetHeader(record));
                return;
            }
            CompositionFoodRowDto row = toRow(record, columns[0]);
            if (row == null) {
                skipped[0]++;
            } else {
                rows.add(row);
            }
        });
        return new CompositionTableDto(rows, skipped[0]);
    }

    private Columns columnsOf(SheetHeader header) {
        Map<Nutrient, Integer> values = new EnumMap<>(Nutrient.class);
        Map<Nutrient, String> units = new EnumMap<>(Nutrient.class);
        CODES.forEach((nutrient, code) -> {
            int at = header.find(name -> name.startsWith(code + " ") && unitOf(name) != null);
            if (at >= 0) {
                values.put(nutrient, at);
                units.put(nutrient, unitOf(header.name(at)));
            }
        });
        return new Columns(header.require(CODE, WORKBOOK), header.require(NAME_DE, WORKBOOK),
                header.indexOf(NAME_EN), values, units);
    }

    /** Null for a row with no code or no name: there is nothing to key or show it by. */
    private CompositionFoodRowDto toRow(List<String> record, Columns columns) {
        String code = Texts.clean(SheetHeader.cell(record, columns.code()), CODE_MAX);
        String nameDe = Texts.cleanLine(SheetHeader.cell(record, columns.nameDe()), NAME_MAX);
        if (code == null || nameDe == null) {
            return null;
        }
        Map<Nutrient, NutrientDto> nutrients = new EnumMap<>(Nutrient.class);
        columns.values().forEach((nutrient, at) -> {
            BigDecimal value = Numbers.toDecimal(SheetHeader.cell(record, at));
            if (value != null) {
                nutrients.put(nutrient, new NutrientDto(value, columns.units().get(nutrient)));
            }
        });
        return new CompositionFoodRowDto(new CompositionKey(CompositionSource.BLS, code),
                nameDe,
                Texts.cleanLine(SheetHeader.cell(record, columns.nameEn()), NAME_MAX),
                code.substring(0, GROUP_PREFIX),
                nutrients, null);
    }

    static String unitOf(String header) {
        Matcher matcher = UNIT.matcher(header);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Where each wanted column sits, and the unit each value header names. */
    private record Columns(int code, int nameDe, int nameEn, Map<Nutrient, Integer> values,
                           Map<Nutrient, String> units) {
    }
}
