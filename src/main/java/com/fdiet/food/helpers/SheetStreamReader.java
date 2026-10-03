package com.fdiet.food.helpers;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * POI's event model: the sheet's XML is parsed as a stream and each cell is
 * handed over as it is read. Shared strings are held once (read-only); cells are
 * not.
 */
@Component
public class SheetStreamReader implements ISheetStreamReader {

    @Override
    public void forEachRow(Path workbook, Consumer<List<String>> row) {
        try (OPCPackage pkg = OPCPackage.open(workbook.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();
            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                return;
            }
            try (InputStream sheet = sheets.next()) {
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(
                        styles, null, strings, new RowCollector(row), new RawNumbers(), false));
                parser.parse(new InputSource(sheet));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + workbook.toAbsolutePath(), e);
        } catch (OpenXML4JException | SAXException | ParserConfigurationException e) {
            throw new UncheckedIOException("Could not read " + workbook.toAbsolutePath(),
                    new IOException(e));
        }
    }

    /**
     * Hands a number back as Excel stores it rather than as the cell's display
     * format shows it, so a value formatted to one decimal is not rounded on
     * the way in. One per read: a formatter keeps caches and is not shared.
     */
    private static final class RawNumbers extends DataFormatter {
        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString) {
            return NumberToTextConverter.toText(value);
        }
    }

    /** Places each cell at its column and hands the row on when it ends. */
    private static final class RowCollector implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final Consumer<List<String>> consumer;
        private final List<String> cells = new ArrayList<>();

        private RowCollector(Consumer<List<String>> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void startRow(int rowNum) {
            cells.clear();
        }

        @Override
        public void endRow(int rowNum) {
            consumer.accept(List.copyOf(cells));
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = cellReference == null ? cells.size() : new CellReference(cellReference).getCol();
            while (cells.size() < column) {
                cells.add("");
            }
            cells.add(formattedValue == null ? "" : formattedValue);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Not data.
        }
    }
}
