package com.fdiet.food.helpers;

import com.fdiet.food.dto.CompositionLinkDto;

import java.nio.file.Path;
import java.util.List;

/**
 * Reads fdiet's Spanish-name crosswalk
 * ({@code reference-data/composition/composition-es/links.csv}) into typed rows.
 * Columns are found by header name; a row naming an unknown source, or without
 * a code or a Spanish name, is refused with its line number.
 */
public interface ICompositionLinkReader {

    List<CompositionLinkDto> read(Path csv);
}
