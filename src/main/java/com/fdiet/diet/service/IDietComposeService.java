package com.fdiet.diet.service;

import com.fdiet.diet.dto.ComposeRequestDto;
import com.fdiet.diet.dto.ComposedFragmentDto;

/**
 * Writes a food added by ration or household measure as the text a recipe
 * holds. It owns no table: the editor appends the fragment to a recipe, and the
 * recipe is stored with the week.
 */
public interface IDietComposeService {

    /**
     * The text a food added by ration or household measure is written as, and
     * that text read back through the parser. Stores nothing.
     */
    ComposedFragmentDto compose(ComposeRequestDto request);
}
