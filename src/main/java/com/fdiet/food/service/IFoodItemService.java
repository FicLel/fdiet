package com.fdiet.food.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.FoodCsvRowDto;
import com.fdiet.food.dto.FoodItemDto;
import com.fdiet.food.dto.FoodItemImportResultDto;
import com.fdiet.food.model.FoodItem;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The one way into the {@code food_items} table. Everything outside this
 * service — the diet module included — asks it rather than the repository.
 *
 * <p>The {@code entitiesBy*} methods hand back entities on purpose: they exist
 * for sibling services that have to build their own associations, which is one
 * layer talking to itself rather than a layer boundary. The DTO methods are the
 * ones the web layer sees.
 */
public interface IFoodItemService {

    /** A page of items, narrowed to those whose commercial name contains {@code name}. */
    PageDto<FoodItemDto> search(String name, int page, int size);

    FoodItemDto findById(Long id);

    /** Stores every row whose EAN is not in the table yet. */
    FoodItemImportResultDto importRows(List<FoodCsvRowDto> rows);

    /**
     * The items whose commercial name is one of {@code names}, keyed by that
     * name normalised the way {@link #normalise} does it.
     *
     * <p>One query per chunk of names, never one per name — this is what keeps
     * a diet import from turning into an N+1. A name the catalogue does not
     * carry is simply absent from the map.
     */
    Map<String, FoodItem> entitiesByName(Collection<String> names);

    /** The items with those ids, keyed by id. A single {@code findAllById}. */
    Map<Long, FoodItem> entitiesByIds(Collection<Long> ids);

    /** The item as a managed entity, for a caller that has to point at it. */
    FoodItem entityById(Long id);

    /** Lower-cased and with its runs of whitespace collapsed; null stays null. */
    static String normalise(String name) {
        return Texts.normaliseName(name);
    }
}
