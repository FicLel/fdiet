package com.fdiet.food.service;

import com.fdiet.common.dto.PageDto;
import com.fdiet.food.dto.BedcaFoodDto;
import com.fdiet.food.dto.BedcaNameRow;
import com.fdiet.food.helpers.NameMatcher;
import com.fdiet.food.mapper.IBedcaFoodMapper;
import com.fdiet.food.model.BedcaFood;
import com.fdiet.food.repository.BedcaFoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The food search box (FD-020). Every name here is copied from
 * bedca_foods.csv, with its id.
 */
class BedcaFoodServiceSearchTest {

    private static final List<BedcaNameRow> CATALOGUE = List.of(
            new BedcaNameRow(757L, "Huevo de codorniz, entero, crudo", null),
            new BedcaNameRow(758L, "Huevo de gallina, escalfado", null),
            new BedcaNameRow(759L, "Huevo de gallina, frito", null),
            new BedcaNameRow(2127L, "Huevo de gallina fresco", null),
            new BedcaNameRow(764L, "Huevo de pato, crudo", null),
            new BedcaNameRow(965L, "Jamón cocido, categoría s/e", null),
            new BedcaNameRow(2271L, "Jamón asado", null),
            new BedcaNameRow(2272L, "Jamón cocido, enlatado", null),
            new BedcaNameRow(2273L, "Jamón serrano", null),
            new BedcaNameRow(1777L, "Jamón ibérico de cebo", null),
            new BedcaNameRow(1067L, "Pan de avena", null),
            new BedcaNameRow(2160L, "Pan blanco, de barra", null),
            new BedcaNameRow(2161L, "Pan blanco, de molde, tostado", null),
            new BedcaNameRow(2163L, "Pan integral", null),
            new BedcaNameRow(2164L, "Pan integral, de molde, tostado", null),
            new BedcaNameRow(2171L, "Pan rallado", null),
            new BedcaNameRow(2283L, "Panceta, frita", null),
            new BedcaNameRow(2399L, "Lechuga", null),
            new BedcaNameRow(1094L, "Té", null));

    private static final Map<Long, String> NAMES = CATALOGUE.stream()
            .collect(Collectors.toMap(BedcaNameRow::id, BedcaNameRow::name));

    private final BedcaFoodRepository repository = mock(BedcaFoodRepository.class);
    private final IBedcaFoodMapper mapper = mock(IBedcaFoodMapper.class);
    private final BedcaFoodService service =
            new BedcaFoodService(repository, mapper, new NameMatcher());

    @BeforeEach
    void catalogue() {
        when(repository.findAllNames()).thenReturn(CATALOGUE);
        when(repository.findAllById(anyIterable())).thenAnswer(call -> {
            Iterable<Long> ids = call.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false).map(BedcaFoodServiceSearchTest::food).toList();
        });
        when(mapper.toDto(any())).thenAnswer(call -> {
            BedcaFood food = call.getArgument(0);
            return new BedcaFoodDto(food.getId(), food.getName(),
                    null, null, null, null, null, null, null, null);
        });
    }

    @Test
    void aPluralFindsTheFoodsNamedInTheSingular() {
        List<String> names = names(service.search("huevos", 0, 20));

        assertThat(names).contains("Huevo de gallina fresco", "Huevo de gallina, frito",
                "Huevo de gallina, escalfado");
        assertThat(names).allMatch(name -> name.startsWith("Huevo"));
    }

    @Test
    void foodsCarryingMoreOfTheWordsComeFirst() {
        List<String> names = names(service.search("pan de molde", 0, 20));

        assertThat(names).startsWith("Pan blanco, de molde, tostado", "Pan integral, de molde, tostado");
        assertThat(names).contains("Pan integral", "Pan rallado", "Pan de avena");
        // A whole word, not a prefix: pan is not panceta.
        assertThat(names).doesNotContain("Panceta, frita");
    }

    @Test
    void aWordTheCatalogueLacksDoesNotHideTheOnesItHas() {
        List<String> names = names(service.search("jamón york", 0, 20));

        assertThat(names).contains("Jamón cocido, categoría s/e", "Jamón cocido, enlatado",
                "Jamón serrano");
        assertThat(names).allMatch(name -> name.startsWith("Jamón"));
    }

    @Test
    void ignoresCaseAndAccents() {
        assertThat(names(service.search("JAMON", 0, 20)))
                .isEqualTo(names(service.search("jamón", 0, 20)));
    }

    @Test
    void aTieGoesTheWaySuggestionsDo() {
        // One word each: the share of the name it covers, then the name.
        assertThat(names(service.search("jamón", 0, 20))).containsExactly(
                "Jamón asado", "Jamón serrano",
                "Jamón cocido, categoría s/e", "Jamón cocido, enlatado", "Jamón ibérico de cebo");
    }

    @Test
    void aWordBeingTypedStillFindsWhatItSpells() {
        assertThat(names(service.search("lechu", 0, 20))).containsExactly("Lechuga");
    }

    @Test
    void aNameTooShortToTokeniseIsStillFoundAsTyped() {
        // "te" is under the shortest word NameMatcher keeps, so Té has no tokens
        // and is reached only by the as-typed tail, in name order with the rest.
        assertThat(names(service.search("té", 0, 20))).contains("Té");
    }

    @Test
    void aTermSharingNothingFindsNothing() {
        PageDto<BedcaFoodDto> page = service.search("hummus", 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        verify(repository, never()).findAllById(anyIterable());
    }

    @Test
    void pagesTheRankingAndLoadsOnlyThePageAsked() {
        PageDto<BedcaFoodDto> second = service.search("pan de molde", 1, 2);

        assertThat(second.totalElements()).isEqualTo(6);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(second.content()).hasSize(2);
        assertThat(names(service.search("pan de molde", 3, 2))).isEmpty();
        verify(repository, times(1)).findAllNames();
        verify(repository, times(1)).findAllById(anyIterable());
    }

    @Test
    void anEmptyTermIsTheAlphabeticalPage() {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(food(2399L))));

        assertThat(names(service.search("  ", 0, 20))).containsExactly("Lechuga");
        verify(repository, never()).findAllNames();
    }

    private static List<String> names(PageDto<BedcaFoodDto> page) {
        return page.content().stream().map(BedcaFoodDto::name).toList();
    }

    private static BedcaFood food(Long id) {
        BedcaFood food = new BedcaFood();
        food.setId(id);
        food.setName(NAMES.get(id));
        return food;
    }
}
