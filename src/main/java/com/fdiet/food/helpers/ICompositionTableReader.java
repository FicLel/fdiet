package com.fdiet.food.helpers;

import com.fdiet.food.dto.CompositionTableDto;
import com.fdiet.food.model.CompositionSource;

import java.nio.file.Path;

/**
 * Reads one open composition table's file into typed rows, finding every column
 * by its header. One implementation per {@link CompositionSource}.
 *
 * <p>Values are carried across as published, each with the unit its header
 * names. A cell that is not a number — {@code traces}, {@code < 0,2},
 * {@code <LOQ}, {@code TR}, {@code -} — is left out, never read as zero.
 */
public interface ICompositionTableReader {

    /** The table this reader understands. */
    CompositionSource source();

    /**
     * The table's foods. {@code folder} is the source's snapshot folder under
     * {@code reference-data/composition/}; the reader knows its file names.
     */
    CompositionTableDto read(Path folder);
}
