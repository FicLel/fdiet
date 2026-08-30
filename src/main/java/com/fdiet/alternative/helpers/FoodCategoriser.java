package com.fdiet.alternative.helpers;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.common.helper.Texts;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.fdiet.alternative.domain.FoodCategory.BEVERAGE;
import static com.fdiet.alternative.domain.FoodCategory.CEREAL;
import static com.fdiet.alternative.domain.FoodCategory.CONDIMENT;
import static com.fdiet.alternative.domain.FoodCategory.DAIRY;
import static com.fdiet.alternative.domain.FoodCategory.EGG;
import static com.fdiet.alternative.domain.FoodCategory.FAT_OIL;
import static com.fdiet.alternative.domain.FoodCategory.FISH;
import static com.fdiet.alternative.domain.FoodCategory.FRUIT;
import static com.fdiet.alternative.domain.FoodCategory.LEGUME;
import static com.fdiet.alternative.domain.FoodCategory.MEAT;
import static com.fdiet.alternative.domain.FoodCategory.NUT;
import static com.fdiet.alternative.domain.FoodCategory.PREPARED_DISH;
import static com.fdiet.alternative.domain.FoodCategory.SUPPLEMENT;
import static com.fdiet.alternative.domain.FoodCategory.SWEET;
import static com.fdiet.alternative.domain.FoodCategory.TUBER;
import static com.fdiet.alternative.domain.FoodCategory.VEGETABLE;

/**
 * The word list that puts a food on a shelf, and nothing else.
 *
 * <p><strong>Why the name and not the published group.</strong> The composition
 * database fills {@code namelevel1} for 182 of its 957 foods and leaves it empty
 * for the other 775, so a category read from it would exist for one food in five
 * and the answer to "what can I swap this for" would depend on which food you
 * happened to ask about. The name is the one thing every row has, and BEDCA
 * names are written head first — {@code Pollo, pechuga, plancha},
 * {@code Aceite de hígado de bacalao} — so the head is the food and everything
 * after it is preparation.
 *
 * <p><strong>So the first word that any rule claims wins.</strong> Reading left
 * to right is what keeps {@code Aceite de hígado de bacalao} an oil rather than
 * offal or fish, {@code Café, con leche} a drink rather than a dairy product,
 * and {@code Flan de huevo} a dessert rather than an egg. A name whose head is
 * not in the list falls through to its later words, which is how
 * {@code Barra chocolate, tipo "bounty"} still lands on {@link FoodCategory#SWEET}.
 *
 * <p>Two-word rules are tried first at each position, because a couple of foods
 * are named after something they are not: {@code judía verde} is a vegetable and
 * not a legume, {@code nuez moscada} a spice and not a nut.
 *
 * <p>Over bedca_foods.csv this claims 956 of the 957 names. The one it does
 * not is left uncategorised on purpose — see {@link IFoodCategoriser}.
 */
@Component
public class FoodCategoriser implements IFoodCategoriser {

    /**
     * A word this short is punctuation more often than food, and every food
     * word the list needs is at least three letters.
     */
    private static final int SHORTEST_WORD = 2;

    /**
     * The families, in the order they were written. Order does not decide
     * anything — a word belongs to exactly one of these, and the flattening
     * below refuses to start if one belongs to two.
     */
    private static final List<Map.Entry<FoodCategory, Set<String>>> RULES = List.of(
            Map.entry(FAT_OIL, Set.of(
                    "aceite", "mantequilla", "margarina", "manteca", "grasa", "sebo")),

            Map.entry(EGG, Set.of(
                    "huevo", "clara", "yema", "tortilla", "ovoproducto")),

            Map.entry(DAIRY, Set.of(
                    "leche", "yogur", "yogurt", "queso", "requeson", "cuajada", "kefir",
                    "nata", "petit", "suisse", "batido", "lacteo", "lactea", "suero", "mato")),

            Map.entry(MEAT, Set.of(
                    "carne", "pollo", "pavo", "pato", "oca", "ganso", "codorniz", "perdiz",
                    "faisan", "conejo", "liebre", "cerdo", "cochinillo", "jamon", "lomo",
                    "panceta", "bacon", "tocino", "chorizo", "salchichon", "salchicha",
                    "morcilla", "longaniza", "butifarra", "sobrasada", "mortadela", "fuet",
                    "embutido", "salami", "ternera", "vaca", "buey", "toro", "cordero",
                    "cabrito", "oveja", "caballo", "cabra", "higado", "rinon", "seso", "callo",
                    "lengua", "corazon", "molleja", "pulmon", "bazo", "criadilla", "tripa",
                    "chuleta", "solomillo", "entrecot", "costilla", "costillar", "pechuga",
                    "muslo", "hamburguesa", "albondiga", "pate", "foie", "cecina", "jabali",
                    "venado", "ciervo", "pichon", "avestruz", "gallina", "morcon", "chicharron",
                    "chistorra", "choped", "lacon", "cabezada", "fiambre")),

            Map.entry(FISH, Set.of(
                    "pescado", "pescadilla", "merluza", "bacalao", "bacaladilla", "atun",
                    "bonito", "sardina", "sardinilla", "boqueron", "anchoa", "salmon",
                    "salmonete", "trucha", "lenguado", "rape", "dorada", "lubina", "besugo",
                    "mero", "rodaballo", "platija", "gallo", "perca", "carpa", "tenca",
                    "anguila", "angula", "congrio", "morena", "mujol", "jurel", "caballa",
                    "chicharro", "palometa", "pez", "espada", "emperador", "cazon", "raya",
                    "arenque", "caviar", "hueva", "surimi", "marisco", "gamba", "quisquilla",
                    "langostino", "camaron", "cigala", "langosta", "bogavante", "cangrejo",
                    "necora", "centollo", "percebe", "mejillon", "almeja", "berberecho",
                    "chirla", "navaja", "ostra", "vieira", "zamburina", "coquina", "calamar",
                    "chipiron", "sepia", "choco", "pulpo", "erizo", "corvina", "breca", "pargo",
                    "sargo", "denton", "jibia", "abadejo", "brotola", "faneca", "lisa", "panga",
                    "sabalo", "tilapia", "virrey", "pota", "anjova", "lucio", "esturion",
                    "lamprea", "melva", "cabracho", "rascacio", "japuta", "bigaro", "lija",
                    "salema", "vieja", "volador", "caracol", "caracola", "fletan", "pijota")),

            Map.entry(LEGUME, Set.of(
                    "legumbre", "lenteja", "garbanzo", "alubia", "judia", "frijol",
                    "habichuela", "haba", "soja", "guisante", "altramuz", "tofu", "seitan")),

            Map.entry(NUT, Set.of(
                    "almendra", "nuez", "avellana", "cacahuete", "pistacho", "anacardo",
                    "pinon", "castana", "pipa", "semilla", "sesamo", "girasol", "lino",
                    "chia", "chufa")),

            Map.entry(CEREAL, Set.of(
                    "pan", "arroz", "pasta", "macarron", "espagueti", "fideo", "tallarin",
                    "raviol", "cereal", "harina", "semola", "avena", "trigo", "maiz", "cebada",
                    "centeno", "espelta", "mijo", "quinoa", "cuscus", "bulgur", "galleta",
                    "tostada", "biscote", "croissant", "bollo", "muesli", "copo", "salvado",
                    "germen", "gluten", "almidon", "pizza", "picatoste", "hojaldre", "masa",
                    "gofio", "gusanito", "torta", "palomita")),

            Map.entry(TUBER, Set.of(
                    "patata", "papa", "boniato", "batata", "yuca", "mandioca", "name",
                    "tapioca")),

            Map.entry(VEGETABLE, Set.of(
                    "verdura", "hortaliza", "lechuga", "tomate", "cebolla", "cebolleta",
                    "cebollino", "zanahoria", "pimiento", "calabacin", "berenjena", "espinaca",
                    "acelga", "brecol", "brocoli", "coliflor", "col", "repollo", "lombarda",
                    "alcachofa", "esparrago", "pepino", "pepinillo", "apio", "puerro", "ajo",
                    "champinon", "seta", "niscalo", "trufa", "remolacha", "nabo", "rabano",
                    "calabaza", "cardo", "endibia", "escarola", "achicoria", "berro",
                    "canonigo", "rucula", "borraja", "grelo", "nabiza", "hinojo", "colinabo",
                    "chirivia", "brote", "germinado", "alga", "aceituna", "alcaparra",
                    "tirabeque", "okra", "bambu", "palmito", "chayote")),

            Map.entry(FRUIT, Set.of(
                    "fruta", "macedonia", "manzana", "pera", "platano", "banana", "naranja",
                    "mandarina", "clementina", "pomelo", "limon", "lima", "uva", "pasa",
                    "fresa", "freson", "frambuesa", "mora", "arandano", "grosella", "cereza",
                    "picota", "ciruela", "albaricoque", "melocoton", "nectarina", "paraguaya",
                    "higo", "breva", "granada", "caqui", "kiwi", "mango", "papaya", "guayaba",
                    "maracuya", "chirimoya", "aguacate", "melon", "sandia", "pina", "coco",
                    "datil", "nispero", "membrillo", "litchi", "lichi", "carambola",
                    "tamarindo", "acerola", "pitaya")),

            Map.entry(SWEET, Set.of(
                    "azucar", "chocolate", "cacao", "bombon", "caramelo", "golosina",
                    "gominola", "turron", "mazapan", "miel", "melaza", "sirope", "jarabe",
                    "almibar", "mermelada", "confitura", "jalea", "compota", "dulce", "flan",
                    "natilla", "mousse", "helado", "sorbete", "tarta", "pastel", "bizcocho",
                    "magdalena", "donut", "churro", "bunuelo", "gelatina", "regaliz",
                    "polvoron", "roscon", "brownie", "edulcorante", "sacarina", "fructosa",
                    "glucosa", "sacarosa", "nougat", "crocanti", "barquillo", "gofre", "crema",
                    "sobao", "rosquilla", "palmera", "ensaimada", "bolleria", "pudin",
                    "pudding")),

            Map.entry(BEVERAGE, Set.of(
                    "agua", "zumo", "jugo", "refresco", "gaseosa", "soda", "cola", "tonica",
                    "bitter", "cerveza", "vino", "cava", "champan", "sidra", "licor", "whisky",
                    "ron", "ginebra", "vodka", "tequila", "anis", "brandy", "conac", "vermut",
                    "orujo", "pacharan", "sangria", "cubata", "cubalibre", "aguardiente",
                    "limonada", "cafe", "te", "infusion", "manzanilla", "poleo", "tila",
                    "horchata", "mosto", "granizado", "bebida", "nectar", "malta")),

            Map.entry(CONDIMENT, Set.of(
                    "salsa", "mayonesa", "mahonesa", "ketchup", "mostaza", "vinagre",
                    "vinagreta", "alioli", "bechamel", "sofrito", "aderezo", "alino",
                    "condimento", "especia", "sal", "pimienta", "pimenton", "oregano",
                    "perejil", "albahaca", "tomillo", "romero", "laurel", "canela", "clavo",
                    "comino", "azafran", "curry", "jengibre", "cilantro", "eneldo", "estragon",
                    "menta", "hierbabuena", "curcuma", "vainilla", "levadura", "guindilla",
                    "chile", "tabasco", "cubito", "miso", "mojo", "gomasio")),

            Map.entry(PREPARED_DISH, Set.of(
                    "lasana", "canelon", "paella", "croqueta", "empanada", "empanadilla",
                    "sopa", "caldo", "consome", "potaje", "fabada", "gazpacho", "salmorejo",
                    "ensaladilla", "ensalada", "sandwich", "bocadillo", "nugget", "guiso",
                    "estofado", "quiche", "pure", "menestra", "pisto", "miga", "souffle",
                    "kebab", "jacobo", "rollito", "flamenquin")),

            Map.entry(SUPPLEMENT, Set.of(
                    "suplemento", "complejo", "vitaminico", "vitamina", "proteico",
                    "dietetico", "sustitutivo")));

    /**
     * The handful of foods named after something they are not. Keyed on the
     * pair of words with their plural {@code -s} dropped, which is the one form
     * both {@code judía verde} and {@code judías verdes} reduce to.
     */
    private static final Map<String, FoodCategory> PAIRS = Map.of(
            "judia verde", VEGETABLE,
            "nuez moscada", CONDIMENT,
            "fruto seco", NUT,
            "maiz dulce", VEGETABLE);

    /** {@link #RULES} flattened, and proof that no word sits in two families. */
    private static final Map<String, FoodCategory> WORDS = flatten();

    @Override
    public FoodCategory of(String name) {
        List<String> words = wordsOf(name);
        for (int at = 0; at < words.size(); at++) {
            if (at + 1 < words.size()) {
                FoodCategory pair = PAIRS.get(
                        withoutPluralS(words.get(at)) + " " + withoutPluralS(words.get(at + 1)));
                if (pair != null) {
                    return pair;
                }
            }
            FoodCategory single = claimedBy(words.get(at));
            if (single != null) {
                return single;
            }
        }
        return null;
    }

    /**
     * The family that claims the word, trying the singular forms a Spanish
     * plural could have come from before giving up. {@code nueces} is asked as
     * {@code nuez}, {@code mejillones} as {@code mejillon} and
     * {@code guisantes} as {@code guisante}; the list is short because only one
     * of those forms is ever in {@link #WORDS}.
     */
    private FoodCategory claimedBy(String word) {
        FoodCategory found = WORDS.get(word);
        if (found != null) {
            return found;
        }
        if (word.length() > 4 && word.endsWith("ces")) {
            found = WORDS.get(word.substring(0, word.length() - 3) + "z");
        }
        if (found == null && word.length() > 4 && word.endsWith("es")) {
            found = WORDS.get(word.substring(0, word.length() - 2));
        }
        if (found == null && word.length() > 3 && word.endsWith("s")) {
            found = WORDS.get(word.substring(0, word.length() - 1));
        }
        return found;
    }

    /** Lower case, without accents, split on anything that is not a letter or digit. */
    private static List<String> wordsOf(String name) {
        String key = Texts.key(name);
        if (key == null) {
            return List.of();
        }
        return Arrays.stream(key.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() >= SHORTEST_WORD)
                .toList();
    }

    private static String withoutPluralS(String word) {
        return word.length() > 3 && word.endsWith("s")
                ? word.substring(0, word.length() - 1)
                : word;
    }

    /**
     * A word claimed by two families would make the answer depend on the order
     * the list happens to be written in, so the class refuses to load instead.
     */
    private static Map<String, FoodCategory> flatten() {
        Map<String, FoodCategory> words = new LinkedHashMap<>();
        for (Map.Entry<FoodCategory, Set<String>> rule : RULES) {
            for (String word : rule.getValue()) {
                FoodCategory taken = words.putIfAbsent(word, rule.getKey());
                if (taken != null) {
                    throw new IllegalStateException(
                            "\"" + word + "\" is claimed by both " + taken + " and " + rule.getKey());
                }
            }
        }
        return Map.copyOf(words);
    }
}
