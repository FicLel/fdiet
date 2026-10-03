package com.fdiet.food.helpers;

import com.fdiet.food.dto.CompositionLinkDto;
import com.fdiet.food.exception.InvalidCompositionDataException;
import com.fdiet.food.model.CompositionSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositionLinkReaderTest {

    private static final String HEADER =
            "source,source_code,name_es,aliases,preferred,edible_portion,edible_portion_fdc_id,reviewed,note\n";

    private final CompositionLinkReader reader = new CompositionLinkReader(new DataReader("fooddata.csv"));

    @TempDir
    Path folder;

    @Test
    void readsARowWithItsAliasesAndItsEdiblePortion() throws IOException {
        List<CompositionLinkDto> links = reader.read(csv(
                "CIQUAL,13004,Aguacate,aguacate; palta ,true,0.74,171705,false,\n"
                        + "BLS,X389000,Guacamole,,true,,,true,BLS recipe\n"));

        assertThat(links).hasSize(2);
        CompositionLinkDto avocado = links.get(0);
        assertThat(avocado.key().source()).isEqualTo(CompositionSource.CIQUAL);
        assertThat(avocado.key().sourceCode()).isEqualTo("13004");
        assertThat(avocado.aliases()).containsExactly("aguacate", "palta");
        assertThat(avocado.preferred()).isTrue();
        assertThat(avocado.ediblePortion()).isEqualByComparingTo("0.74");
        assertThat(avocado.ediblePortionFdcId()).isEqualTo(171705);
        assertThat(avocado.reviewed()).isFalse();

        CompositionLinkDto guacamole = links.get(1);
        assertThat(guacamole.aliases()).isEmpty();
        assertThat(guacamole.ediblePortion()).isNull();
        assertThat(guacamole.reviewed()).isTrue();
    }

    @Test
    void refusesASourceItDoesNotKnow() throws IOException {
        Path file = csv("BEDCA,2399,Lechuga,,true,,,false,\n");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidCompositionDataException.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("BEDCA");
    }

    /** An edible portion is a fraction of the food; 74 (a percentage) is a typing slip, not a food. */
    @Test
    void refusesAnEdiblePortionOutsideZeroToOne() throws IOException {
        Path file = csv("CIQUAL,13004,Aguacate,,true,74,171705,false,\n");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidCompositionDataException.class)
                .hasMessageContaining("edible_portion");
    }

    @Test
    void refusesAFileMissingAColumn() throws IOException {
        Path file = folder.resolve("links.csv");
        Files.writeString(file, "source,source_code,name_es\nCIQUAL,1,Pan\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidCompositionDataException.class)
                .hasMessageContaining("aliases");
    }

    private Path csv(String rows) throws IOException {
        Path file = folder.resolve("links.csv");
        Files.writeString(file, HEADER + rows, StandardCharsets.UTF_8);
        return file;
    }
}
