package com.fdiet.food.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.food.dto.CompositionFoodDto;
import com.fdiet.food.dto.CompositionFoodRowDto;
import com.fdiet.food.dto.CompositionKey;
import com.fdiet.food.dto.CompositionStoreResultDto;
import com.fdiet.food.dto.CompositionSuggestionDto;
import com.fdiet.food.model.CompositionFood;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The one way into {@code composition_foods} — CIQUAL 2025 and BLS 4.0, with
 * fdiet's Spanish names. Since FD-033 phase D it is the food every recipe
 * ingredient, journal extra, reference row and alternative points at; the diet
 * resolves a week through its batched calls, so none of them turns into an N+1.
 */
public interface ICompositionFoodService {

    /**
     * A page of foods. Without {@code name}, by source and English name. With
     * it, the foods whose Spanish name or aliases share at least one of its
     * words, most words shared first; then any food whose Spanish, English or
     * original name contains the term as typed ({@code lettuce}, {@code laitue},
     * {@code lechu}), so a food with no Spanish name yet can still be found.
     */
    PageDto<CompositionFoodDto> search(String name, int page, int size);

    CompositionFoodDto findById(Long id);

    /** The food as a managed entity, for a caller that has to point at it. */
    CompositionFood entityById(Long id);

    /**
     * The foods those names name exactly — a Spanish name or alias from the
     * crosswalk, case and accents ignored — keyed by the name normalised
     * through {@link com.fdiet.common.helper.Texts#normaliseName} (the key the
     * diet's resolver looks names up by). A name nothing answers, or
     * one two foods share with neither preferred, is absent. One query however
     * many names.
     */
    Map<String, CompositionFood> entitiesByName(Collection<String> names);

    /** The foods with those ids, keyed by id. A single {@code findAllById}. */
    Map<Long, CompositionFood> entitiesByIds(Collection<Long> ids);

    /**
     * The ids of the foods those stable keys name — {@code (source, source_code)},
     * which survive every sync while an id differs per installation. A key
     * nothing answers is absent. At most one query (the index), however many keys.
     */
    Map<CompositionKey, Long> idsByKey(Collection<CompositionKey> keys);

    /**
     * Every food the crosswalk gives a Spanish name, in one query: the foods a
     * name-based judgement (a food's family, its state) can be made about at all.
     * Offered for {@code com.fdiet.alternative}, whose question — which foods share
     * this one's family — is read off Spanish names the database cannot filter on.
     * Bounded by the crosswalk (123 rows today), not by the ten thousand foods.
     */
    List<CompositionFood> entitiesNamed();

    /**
     * The crosswalked foods whose Spanish names best fit some ingredient text,
     * best first. Offers, not decisions — nothing in this module assigns a
     * suggestion to anything. Answered from the in-memory index: no query.
     */
    List<CompositionSuggestionDto> suggest(String text, int limit);

    /** Whether nothing has been synced yet. */
    boolean isEmpty();

    /**
     * Stores a synced batch: rows whose {@code (source, source_code)} is new are
     * inserted, rows already held are written over in place with their id kept,
     * so re-running a sync is safe and renumbers nothing.
     */
    CompositionStoreResultDto storeAll(List<CompositionFoodRowDto> rows);
}
