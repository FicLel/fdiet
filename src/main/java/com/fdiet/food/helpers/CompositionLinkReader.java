package com.fdiet.food.helpers;

import com.fdiet.common.helper.Numbers;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.exception.InvalidCompositionDataException;
import com.fdiet.food.model.CompositionSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class CompositionLinkReader implements ICompositionLinkReader {

    private static final String SOURCE = "source";
    private static final String SOURCE_CODE = "source_code";
    private static final String NAME_ES = "name_es";
    private static final String ALIASES = "aliases";
    private static final String PREFERRED = "preferred";
    private static final String EDIBLE_PORTION = "edible_portion";
    private static final String EDIBLE_PORTION_FDC_ID = "edible_portion_fdc_id";
    private static final String REVIEWED = "reviewed";

    private static final int NAME_MAX = 255;
    private static final int CODE_MAX = 32;

    private final DataReader dataReader;

    public CompositionLinkReader(DataReader dataReader) {
        this.dataReader = dataReader;
    }

    @Override
    public List<CompositionLinkDto> read(Path csv) {
        List<List<String>> records = dataReader.read(csv);
        if (records.isEmpty()) {
            return List.of();
        }
        SheetHeader header = new SheetHeader(records.get(0));
        String file = csv.getFileName().toString();
        Columns columns = new Columns(
                header.require(SOURCE, file), header.require(SOURCE_CODE, file),
                header.require(NAME_ES, file), header.require(ALIASES, file),
                header.require(PREFERRED, file), header.require(EDIBLE_PORTION, file),
                header.require(EDIBLE_PORTION_FDC_ID, file), header.require(REVIEWED, file));

        List<CompositionLinkDto> links = new ArrayList<>(records.size());
        for (int at = 1; at < records.size(); at++) {
            List<String> record = records.get(at);
            if (record.stream().allMatch(cell -> Texts.trimToNull(cell) == null)) {
                continue;
            }
            // Record 0 is the header on line 1, so record n sits on line n + 1.
            links.add(toLink(record, columns, file + " line " + (at + 1)));
        }
        return links;
    }

    private CompositionLinkDto toLink(List<String> record, Columns columns, String where) {
        CompositionSource source = sourceOf(SheetHeader.cell(record, columns.source()), where);
        String code = Texts.clean(SheetHeader.cell(record, columns.code()), CODE_MAX);
        String nameEs = Texts.cleanLine(SheetHeader.cell(record, columns.nameEs()), NAME_MAX);
        if (code == null || nameEs == null) {
            throw new InvalidCompositionDataException(where + ": a code and a Spanish name are both required");
        }
        String aliases = SheetHeader.cell(record, columns.aliases());
        return new CompositionLinkDto(
                new CompositionKey(source, code),
                nameEs,
                CompositionLinkDto.splitAliases(aliases).stream()
                        .map(alias -> Texts.cleanLine(alias, NAME_MAX))
                        .toList(),
                Boolean.parseBoolean(Texts.trimToNull(SheetHeader.cell(record, columns.preferred()))),
                ediblePortionOf(SheetHeader.cell(record, columns.ediblePortion()), where),
                Numbers.toInteger(SheetHeader.cell(record, columns.fdcId())),
                Boolean.parseBoolean(Texts.trimToNull(SheetHeader.cell(record, columns.reviewed()))));
    }

    private CompositionSource sourceOf(String value, String where) {
        String trimmed = Texts.trimToNull(value);
        try {
            return CompositionSource.valueOf(Objects.requireNonNull(trimmed).toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new InvalidCompositionDataException(where + ": unknown source \"" + value + "\"");
        }
    }

    /** Blank is "no SR Legacy food fits" and stays null; anything else must be a fraction in (0, 1]. */
    private BigDecimal ediblePortionOf(String value, String where) {
        if (Texts.trimToNull(value) == null) {
            return null;
        }
        BigDecimal portion = Numbers.toDecimal(value);
        if (portion == null || portion.signum() <= 0 || portion.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidCompositionDataException(where + ": edible_portion must be in (0, 1], not \""
                    + value + "\"");
        }
        return portion;
    }

    private record Columns(int source, int code, int nameEs, int aliases, int preferred,
                           int ediblePortion, int fdcId, int reviewed) {
    }
}
