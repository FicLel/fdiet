package com.fdiet.food.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.common.helper.Texts;
import com.fdiet.food.dto.BedcaCsvRowDto;
import com.fdiet.food.dto.BedcaFoodDto;
import com.fdiet.food.dto.BedcaStoreResultDto;
import com.fdiet.food.dto.FoodSuggestionDto;
import com.fdiet.food.model.BedcaFood;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The one way into the {@code bedca_foods} table — the generic foods a diet is
 * written in. Everything outside this service, the diet module included, asks
 * it rather than the repository.
 *
 * <p>It is shaped like {@link IFoodItemService} on purpose: the diet resolves
 * a week against both halves of the catalogue through the same two batched
 * calls, so neither turns into an N+1.
 */
public interface IBedcaFoodService {

    /** A page of foods, narrowed to those whose name contains {@code name}. */
    PageDto<BedcaFoodDto> search(String name, int page, int size);

    BedcaFoodDto findById(Long id);

    /** The food as a managed entity, for a caller that has to point at it. */
    BedcaFood entityById(Long id);

    /**
     * The foods with those names, keyed by the name normalised through
     * {@link #normalise}. One query per chunk, never one per name; a name the
     * database does not carry is simply absent.
     */
    Map<String, BedcaFood> entitiesByName(Collection<String> names);

    /** The foods with those ids, keyed by id. A single {@code findAllById}. */
    Map<Long, BedcaFood> entitiesByIds(Collection<Long> ids);

    /**
     * The foods whose names best fit some ingredient text, best first.
     *
     * <p>Offers, not decisions — nothing in this module assigns a suggestion to
     * anything. Answered from an in-memory index of all 957 names, so a page of
     * unmatched ingredients costs no extra query.
     */
    List<FoodSuggestionDto> suggest(String text, int limit);

    /**
     * Stores a synced batch: rows whose id is new are inserted, rows already
     * held are written over, so re-running a sync brings corrections in and
     * renumbers nothing.
     */
    BedcaStoreResultDto storeAll(List<BedcaCsvRowDto> rows);

    /** Lower-cased and with its runs of whitespace collapsed; null stays null. */
    static String normalise(String name) {
        return Texts.normaliseName(name);
    }
}
