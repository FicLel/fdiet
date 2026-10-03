package com.fdiet.food.helpers;

import com.fdiet.common.helper.Texts;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reduces a food name to the words that carry it, and measures the overlap.
 *
 * <p>A diet writes {@code 2 lonchas de pavo cocido} and the catalogue holds
 * {@code Pavo, crudo}: the quantity, the serving word and the cooking state are
 * noise, and {@code pavo} is the food. So the numbers, the units and a list of
 * everyday filler words are dropped, plurals are folded onto their singular —
 * Spanish {@code nueces → nuez}, {@code huevos → huevo} — and what is left is
 * compared.
 *
 * <p>The score is coverage of the <em>food's</em> words, not the ingredient's:
 * an ingredient line is long and full of preparation, while a catalogue name is
 * short and exact, so "is every word of the food present in this line" is the
 * question worth asking. It is still only a ranking — {@code 1 pan integral}
 * covers every word of {@code Pan rallado} and is not the same food.
 */
@Component
public class NameMatcher implements INameMatcher {

    private static final int SHORTEST_TOKEN = 3;

    /**
     * Words that say how much, how it is served or how it is cooked. None of
     * them tells you which food it is.
     */
    private static final Set<String> FILLER = Set.of(
            "del", "los", "las", "una", "unos", "unas", "con", "sin", "para",
            "muy", "tipo", "casero", "casera", "caseros", "caseras",
            "suave", "suaves", "natural", "naturales",
            "fresco", "fresca", "frescos", "frescas",
            "grande", "grandes", "pequeno", "pequena", "pequenos", "pequenas",
            "mediano", "mediana", "medianos", "medianas",
            "vaso", "vasos", "loncha", "lonchas", "rodaja", "rodajas",
            "diente", "dientes", "filete", "filetes", "tostada", "tostadas",
            "palito", "palitos", "baston", "bastones", "pizca", "dado", "dados",
            "lamina", "laminas", "trozo", "trozos", "puñado", "punado",
            "cdta", "cda", "cucharada", "cucharadita", "taza", "unidad", "unidades",
            "gramo", "gramos", "kilo", "kilos", "litro", "litros", "mililitro", "mililitros",
            "cdas", "cdtas", "cucharadas", "cucharaditas", "sopera", "soperas");

    /**
     * The abbreviations a diet is written in that stand for several words of a
     * catalogue name. {@code 1 cdta AOVE} is the most repeated ingredient of
     * example-ui.xlsx and shares no word with {@code Aceite de oliva virgen
     * extra} until it is spelled out.
     */
    private static final Map<String, List<String>> ABBREVIATIONS = Map.of(
            "aove", List.of("aceite", "oliva", "virgen", "extra"));

    @Override
    public Set<String> tokens(String text) {
        String key = Texts.key(text);
        if (key == null) {
            return Set.of();
        }
        // Anything in brackets is a note or a quantity, never the food itself.
        String stripped = key.replaceAll("\\([^)]*\\)", " ").toLowerCase();
        Set<String> tokens = new LinkedHashSet<>();
        for (String word : stripped.split("[^a-z0-9ñ]+")) {
            List<String> spelled = ABBREVIATIONS.get(word);
            if (spelled != null) {
                tokens.addAll(spelled);
                continue;
            }
            String singular = singular(word);
            if (singular.length() >= SHORTEST_TOKEN && !FILLER.contains(singular)
                    && !singular.chars().allMatch(Character::isDigit)) {
                tokens.add(singular);
            }
        }
        return tokens;
    }

    @Override
    public int score(Set<String> ingredientTokens, List<String> foodTokens) {
        if (ingredientTokens.isEmpty() || foodTokens.isEmpty()) {
            return 0;
        }
        return (int) Math.round(100.0 * shared(ingredientTokens, foodTokens) / foodTokens.size());
    }

    @Override
    public int shared(Set<String> searchTokens, List<String> foodTokens) {
        return (int) foodTokens.stream().filter(searchTokens::contains).count();
    }

    /**
     * Spanish plurals, far enough for a food name: {@code nueces → nuez},
     * {@code huevos → huevo}, {@code judias → judia}.
     */
    private static String singular(String word) {
        if (word.length() > 4 && word.endsWith("ces")) {
            return word.substring(0, word.length() - 3) + "z";
        }
        if (word.length() > 4 && word.endsWith("es")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
