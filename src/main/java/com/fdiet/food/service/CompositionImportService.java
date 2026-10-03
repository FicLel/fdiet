package com.fdiet.food.service;

import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.dto.CompositionStoreResultDto;
import com.fdiet.food.dto.CompositionSyncSummaryDto;
import com.fdiet.food.dto.CompositionTableDto;
import com.fdiet.food.exception.InvalidCompositionDataException;
import com.fdiet.food.helpers.ICompositionLinkReader;
import com.fdiet.food.helpers.ICompositionTableReader;
import com.fdiet.food.helpers.NameIndex;
import com.fdiet.food.model.CompositionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the two tables and the crosswalk, joins them in memory and hands the
 * rows to {@link ICompositionFoodService#storeAll} in one call.
 *
 * <p>All file reading happens before the write transaction opens. The crosswalk
 * is checked first: a Spanish name two rows claim without exactly one preferred
 * stops the sync before anything is written, because the alternative is a diet
 * matched to whichever row happened to load last. A row naming a code neither
 * table holds is skipped and reported, as the reference sync reports an unknown
 * food.
 */
@Service
public class CompositionImportService implements ICompositionImportService {

    private static final Logger log = LoggerFactory.getLogger(CompositionImportService.class);

    /** Each source's snapshot folder, relative to {@code fdiet.composition.data-path}. */
    private static final Map<CompositionSource, String> FOLDERS = new EnumMap<>(Map.of(
            CompositionSource.CIQUAL, "ciqual-2025",
            CompositionSource.BLS, "bls-4.0"));

    /** fdiet's crosswalk, relative to {@code fdiet.composition.data-path}. */
    private static final String LINKS = "composition-es/links.csv";

    private final Map<CompositionSource, ICompositionTableReader> readers;
    private final ICompositionLinkReader linkReader;
    private final ICompositionFoodService compositionFoodService;
    private final Path root;

    public CompositionImportService(List<ICompositionTableReader> readers,
                                    ICompositionLinkReader linkReader,
                                    ICompositionFoodService compositionFoodService,
                                    @Value("${fdiet.composition.data-path:reference-data/composition}")
                                    String dataPath) {
        this.readers = new EnumMap<>(CompositionSource.class);
        readers.forEach(reader -> this.readers.put(reader.source(), reader));
        this.linkReader = linkReader;
        this.compositionFoodService = compositionFoodService;
        this.root = Path.of(dataPath);
    }

    /** O(n) in the foods of both tables plus the crosswalk rows; the store is one batched upsert. */
    @Override
    public CompositionSyncSummaryDto sync() {
        Map<CompositionKey, CompositionLinkDto> links = linksByKey(linkReader.read(root.resolve(LINKS)));

        Map<CompositionSource, Integer> counts = new EnumMap<>(CompositionSource.class);
        List<CompositionFoodRowDto> rows = new ArrayList<>();
        int skipped = 0;
        int linked = 0;
        int withoutEnergy = 0;
        for (CompositionSource source : CompositionSource.values()) {
            CompositionTableDto table = readerOf(source).read(root.resolve(FOLDERS.get(source)));
            counts.put(source, table.rows().size());
            skipped += table.skipped();
            for (CompositionFoodRowDto row : table.rows()) {
                CompositionLinkDto link = links.remove(row.key());
                linked += link == null ? 0 : 1;
                withoutEnergy += row.energyPublished() ? 0 : 1;
                rows.add(row.withLink(link));
            }
        }
        List<String> unmatched = links.values().stream()
                .map(link -> link.key() + " (" + link.nameEs() + "): no such food in the source")
                .toList();

        CompositionStoreResultDto stored = compositionFoodService.storeAll(rows);
        log.info("Synced {} composition foods ({} inserted, {} updated, {} skipped); {} crosswalked, "
                        + "{} crosswalk rows unmatched, {} without energy",
                rows.size(), stored.inserted(), stored.updated(), skipped, linked, unmatched.size(),
                withoutEnergy);

        return new CompositionSyncSummaryDto(
                counts.get(CompositionSource.CIQUAL), counts.get(CompositionSource.BLS), skipped,
                stored.inserted(), stored.updated(), linked, unmatched, withoutEnergy,
                Arrays.stream(CompositionSource.values()).map(CompositionSource::attribution).toList());
    }

    /**
     * The crosswalk by food, refused whole when a food is listed twice or a
     * Spanish name is shared without exactly one preferred row.
     */
    static Map<CompositionKey, CompositionLinkDto> linksByKey(List<CompositionLinkDto> links) {
        Map<CompositionKey, CompositionLinkDto> byKey = new LinkedHashMap<>();
        List<NameIndex.Entry<CompositionKey>> entries = new ArrayList<>(links.size());
        for (CompositionLinkDto link : links) {
            if (byKey.putIfAbsent(link.key(), link) != null) {
                throw new InvalidCompositionDataException("The crosswalk lists " + link.key() + " twice");
            }
            entries.add(new NameIndex.Entry<>(link.key(), link.names(), link.preferred()));
        }
        List<String> conflicts = NameIndex.of(entries).conflicts();
        if (!conflicts.isEmpty()) {
            throw new InvalidCompositionDataException("The crosswalk gives these names to several foods "
                    + "without exactly one marked preferred: " + String.join(", ", conflicts));
        }
        return byKey;
    }

    private ICompositionTableReader readerOf(CompositionSource source) {
        ICompositionTableReader reader = readers.get(source);
        if (reader == null) {
            throw new IllegalStateException("No reader for " + source);
        }
        return reader;
    }
}
