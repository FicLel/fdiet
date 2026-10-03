package com.fdiet.food.helpers;

import com.fdiet.common.helper.Numbers;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionTableDto;
import com.fdiet.food.dto.NutrientDto;
import com.fdiet.food.exception.InvalidCompositionDataException;
import com.fdiet.food.model.CompositionSource;
import com.fdiet.food.model.Nutrient;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CIQUAL 2025 (ANSES): the English workbook for the values and the English
 * names, and {@code alim_*.xml} for the French names the workbook leaves out.
 *
 * <p>Energy is the kcal column of Regulation (EU) 1169/2011, the figure a label
 * carries; protein is CIQUAL's own (nitrogen × Jones factor), not the crude
 * 6.25 column. Decimal commas are read; qualified cells are left out.
 */
@Component
public class CiqualTableReader implements ICompositionTableReader {

    static final String WORKBOOK = "Table Ciqual 2025_ENG_2025_11_03.xlsx";
    static final String FOODS_XML = "alim_2025_11_03.xml";

    private static final String CODE = "alim_code";
    private static final String NAME_EN = "alim_nom_eng";
    private static final String GROUP = "alim_ssgrp_code";

    // The food element of alim_*.xml and the two fields read from it.
    private static final String XML_FOOD = "ALIM";
    private static final String XML_CODE = "alim_code";
    private static final String XML_NAME_FR = "alim_nom_fr";

    private static final int NAME_MAX = 255;
    private static final int CODE_MAX = 32;
    private static final int GROUP_MAX = 16;

    /** {@code Protein (g/100g)}: the unit is what sits between the bracket and {@code 100g}. */
    private static final Pattern UNIT = Pattern.compile("\\(([^\\s/()]+)[\\s/]+100 ?g\\)$");

    /** Each component's header, without its unit, and the unit it must be published in. */
    private static final Map<Nutrient, String[]> COLUMNS = new EnumMap<>(Map.ofEntries(
            Map.entry(Nutrient.ENERGY, new String[]{"Energy, Regulation EU No 1169 2011", "kcal"}),
            Map.entry(Nutrient.PROTEIN, new String[]{"Protein", "g"}),
            Map.entry(Nutrient.FAT, new String[]{"Fat", "g"}),
            Map.entry(Nutrient.SATURATED_FAT, new String[]{"FA saturated", "g"}),
            Map.entry(Nutrient.CARBOHYDRATES, new String[]{"Carbohydrate", "g"}),
            Map.entry(Nutrient.SUGARS, new String[]{"Sugars", "g"}),
            Map.entry(Nutrient.FIBER, new String[]{"Fibres", "g"}),
            Map.entry(Nutrient.WATER, new String[]{"Water", "g"}),
            Map.entry(Nutrient.SODIUM, new String[]{"Sodium", "mg"}),
            Map.entry(Nutrient.POTASSIUM, new String[]{"Potassium", "mg"}),
            Map.entry(Nutrient.CALCIUM, new String[]{"Calcium", "mg"}),
            Map.entry(Nutrient.IRON, new String[]{"Iron", "mg"}),
            Map.entry(Nutrient.CHOLESTEROL, new String[]{"Cholesterol", "mg"}),
            Map.entry(Nutrient.VITAMIN_C, new String[]{"Vitamin C", "mg"})));

    private final ISheetStreamReader sheetReader;

    public CiqualTableReader(ISheetStreamReader sheetReader) {
        this.sheetReader = sheetReader;
    }

    @Override
    public CompositionSource source() {
        return CompositionSource.CIQUAL;
    }

    /** One pass over the workbook, one over the XML: O(n) in the 3,484 foods. */
    @Override
    public CompositionTableDto read(Path folder) {
        Map<String, String> frenchNames = frenchNames(folder.resolve(FOODS_XML));
        List<CompositionFoodRowDto> rows = new ArrayList<>();
        int[] skipped = {0};
        Columns[] columns = {null};
        sheetReader.forEachRow(folder.resolve(WORKBOOK), record -> {
            if (columns[0] == null) {
                columns[0] = columnsOf(new SheetHeader(record));
                return;
            }
            CompositionFoodRowDto row = toRow(record, columns[0], frenchNames);
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
        COLUMNS.forEach((nutrient, expected) -> {
            int at = header.find(name -> name.startsWith(expected[0] + " (")
                    && expected[1].equals(unitOf(name)));
            if (at >= 0) {
                values.put(nutrient, at);
            }
        });
        return new Columns(header.require(CODE, WORKBOOK), header.require(NAME_EN, WORKBOOK),
                header.indexOf(GROUP), values);
    }

    /** Null for a row with no code: there is nothing to key it on. */
    private CompositionFoodRowDto toRow(List<String> record, Columns columns,
                                        Map<String, String> frenchNames) {
        String code = Texts.clean(SheetHeader.cell(record, columns.code()), CODE_MAX);
        String nameEn = Texts.cleanLine(SheetHeader.cell(record, columns.nameEn()), NAME_MAX);
        if (code == null) {
            return null;
        }
        String nameFr = Texts.cleanLine(frenchNames.get(code), NAME_MAX);
        String original = nameFr != null ? nameFr : nameEn;
        if (original == null) {
            return null;
        }
        Map<Nutrient, NutrientDto> nutrients = new EnumMap<>(Nutrient.class);
        columns.values().forEach((nutrient, at) -> {
            BigDecimal value = Numbers.toDecimal(SheetHeader.cell(record, at));
            if (value != null) {
                nutrients.put(nutrient, new NutrientDto(value, COLUMNS.get(nutrient)[1]));
            }
        });
        return new CompositionFoodRowDto(new CompositionKey(CompositionSource.CIQUAL, code),
                original, nameEn,
                Texts.clean(SheetHeader.cell(record, columns.group()), GROUP_MAX),
                nutrients, null);
    }

    static String unitOf(String header) {
        Matcher matcher = UNIT.matcher(header);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** {@code alim_code} → {@code alim_nom_fr}, read as a stream. */
    private Map<String, String> frenchNames(Path xml) {
        Map<String, String> names = new HashMap<>();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream in = Files.newInputStream(xml)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            String code = null;
            String name = null;
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                switch (reader.getLocalName()) {
                    case XML_FOOD -> {
                        code = null;
                        name = null;
                    }
                    case XML_CODE -> code = Texts.trimToNull(reader.getElementText());
                    case XML_NAME_FR -> name = reader.getElementText();
                    default -> {
                        continue;
                    }
                }
                if (code != null && name != null) {
                    names.put(code, name);
                }
            }
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + xml.toAbsolutePath(), e);
        } catch (XMLStreamException e) {
            throw new InvalidCompositionDataException("Could not read " + xml + ": " + e.getMessage());
        }
        return names;
    }

    /** Where each wanted column sits; {@code group} may be -1. */
    private record Columns(int code, int nameEn, int group, Map<Nutrient, Integer> values) {
    }
}
