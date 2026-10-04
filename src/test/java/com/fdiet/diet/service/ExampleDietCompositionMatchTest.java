package com.fdiet.diet.service;

import com.fdiet.common.helper.Texts;
import com.fdiet.diet.dto.DietRequestDto;
import com.fdiet.diet.dto.DishIngredient;
import com.fdiet.diet.helpers.DietWorkbookReader;
import com.fdiet.diet.helpers.MealTextParser;
import com.fdiet.food.dto.CompositionIndexRow;
import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.helpers.CompositionLinkReader;
import com.fdiet.food.helpers.DataReader;
import com.fdiet.food.helpers.NameMatcher;
import com.fdiet.food.mapper.ICompositionFoodMapper;
import com.fdiet.food.model.CompositionFood;
import com.fdiet.food.repository.CompositionFoodRepository;
import com.fdiet.food.service.CompositionFoodService;
import com.fdiet.food.service.IFoodItemService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FD-033's acceptance floor, measured: importing example-ui.xlsx ("Dieta 1")
 * must match at least 144 of its 210 ingredients outright against the open
 * composition tables — what phase B reached. (The original floor, 41, was what
 * BEDCA matched before FD-033 retired it.)
 *
 * <p>The week is read by the real importer and parser, and every ingredient name
 * is put to the real {@link FoodResolverService} over the real
 * {@link CompositionFoodService}: exactly, and once more without its size words,
 * the rule every import and the V18 re-match go through. Only the table is
 * replaced, by the committed crosswalk (composition-es/links.csv), and the branded
 * catalogue by one that answers nothing — no database is touched.
 */
class ExampleDietCompositionMatchTest {

    private static final Path WORKBOOK = Path.of("example-ui.xlsx");
    private static final String SHEET = "Dieta 1";
    private static final Path LINKS = Path.of("reference-data/composition/composition-es/links.csv");

    /** What the crosswalk reached in phase B; no later phase may lose any (AC D3, E8). */
    private static final int PHASE_B = 144;
    private static final int INGREDIENTS = 210;

    private static final String CAPTURED = "captured";

    @Test
    void matchesAtLeastWhatPhaseBReached() throws IOException {
        List<String> names = ingredientNamesOfTheExampleWeek();
        FoodResolverService resolver =
                new FoodResolverService(serviceOverTheCrosswalk(), mock(IFoodItemService.class), 100);

        Map<String, FoodMatch> resolved = resolver.resolve(names);

        long matched = names.stream()
                .map(Texts::normaliseName)
                .filter(name -> resolved.containsKey(name) && resolved.get(name).compositionFood() != null)
                .count();

        System.out.printf("example-ui.xlsx %s: %d of %d ingredients matched outright "
                + "against the composition crosswalk%n", SHEET, matched, names.size());
        assertThat(names).hasSize(INGREDIENTS);
        assertThat(matched).isGreaterThanOrEqualTo(PHASE_B);
    }

    private static List<String> ingredientNamesOfTheExampleWeek() throws IOException {
        IDietService dietService = mock(IDietService.class);
        DietImportService importService =
                new DietImportService(new DietWorkbookReader(), new MealTextParser(), dietService);
        // Nothing is stored: the week is captured on its way to the diet service, and the
        // import stops there.
        when(dietService.create(any())).thenThrow(new IllegalStateException(CAPTURED));
        try (InputStream workbook = Files.newInputStream(WORKBOOK)) {
            assertThatThrownBy(() -> importService.importWorkbook(workbook, 1L, SHEET, null, null, null, null))
                    .hasMessage(CAPTURED);
        }
        ArgumentCaptor<DietRequestDto> week = ArgumentCaptor.forClass(DietRequestDto.class);
        verify(dietService).create(week.capture());

        List<String> names = new ArrayList<>();
        week.getValue().days().forEach(day -> day.meals().forEach(meal -> meal.dishes().forEach(dish ->
                dish.recipe().ingredients().stream().map(DishIngredient::name).forEach(names::add))));
        return names;
    }

    /** The real service, over a repository that holds the crosswalk's foods and nothing else. */
    @SuppressWarnings("unchecked")
    private static CompositionFoodService serviceOverTheCrosswalk() {
        List<CompositionLinkDto> links = new CompositionLinkReader(new DataReader("fooddata.csv")).read(LINKS);
        List<CompositionIndexRow> rows = new ArrayList<>();
        Map<Long, CompositionFood> foods = new LinkedHashMap<>();
        long id = 0;
        for (CompositionLinkDto link : links) {
            id++;
            rows.add(new CompositionIndexRow(id, link.key().source(), link.key().sourceCode(),
                    link.nameEs(), link.aliasesJoined(), link.preferred(), null, link.nameEs()));
            CompositionFood food = new CompositionFood();
            food.setId(id);
            food.setNameEs(link.nameEs());
            foods.put(id, food);
        }

        CompositionFoodRepository repository = mock(CompositionFoodRepository.class);
        when(repository.findAllIndexRows()).thenReturn(rows);
        when(repository.findAllById(anyIterable())).thenAnswer(call -> {
            List<CompositionFood> found = new ArrayList<>();
            ((Iterable<Long>) call.getArgument(0)).forEach(key -> found.add(foods.get(key)));
            return found.stream().filter(Objects::nonNull).toList();
        });
        return new CompositionFoodService(repository, mock(ICompositionFoodMapper.class), new NameMatcher(), 500);
    }
}
