import { computed, reactive, ref, shallowRef } from 'vue'
import { catalogueApi } from '@/api/catalogue'
import { dietsApi } from '@/api/diets'
import { referenceApi } from '@/api/reference'
import type {
  BedcaFood,
  DishIngredient,
  FoodItem,
  FoodMeasure,
  FoodSuggestion,
  HouseholdMeasure,
} from '@/api/types'
import { isUnweighed, locate, useDietDraft, type IngredientAt } from './dietDraft'
import { useReference } from './reference'

/**
 * Matching an ingredient to a food.
 *
 * **The machine offers and the nutritionist decides.** The import matches only
 * exactly — `lechuga` finds `Lechuga` and nothing else — because similarity
 * alone puts `1 pan integral` on `Pan rallado` and `2 lonchas de jamón serrano`
 * on `Jamón asado`: close enough to score well, wrong enough to put a false
 * figure in someone's diet. So everything else arrives unmatched, with ranked
 * candidates attached, and a person picks one.
 *
 * **A match is written the moment it is made, not at publish.** It changes
 * nothing the patient reads — the diet still says what the nutritionist wrote —
 * it only records which catalogue food those words mean, which is why it has an
 * endpoint of its own rather than travelling with the week.
 *
 * The consequence: an ingredient of a cell with unpublished changes cannot be
 * matched. It has no stored row for the PATCH to address, since publishing is
 * about to write it afresh. That cell is published first.
 */

/** How long a pause in the typing means the query is ready to be run. */
const SEARCH_DEBOUNCE_MS = 300

/** Enough of the catalogue to choose from without becoming a list to read. */
const SEARCH_SIZE = 25

/** One page holds every unmatched ingredient of a week; 210 is the whole of one. */
const SUGGESTION_PAGE = 200

/** Which half of the catalogue is being searched. They answer different questions. */
export type CatalogueHalf = 'bedca' | 'branded'

/** A candidate to match against, from either half, in the one shape the list draws. */
export interface CatalogueResult {
  key: string
  bedcaFoodId: number | null
  foodItemId: number | null
  name: string
  /** The food group for a generic food, the brand for a product. */
  note: string | null
  /** The EAN, which only a branded product has. */
  code: string | null
  /** Energy per 100 g, or null where the source published none. */
  kcalPer100: number | null
  /** 0–100, how much of the name the ingredient's words account for. */
  score: number | null
}

const draft = useDietDraft()
const reference = useReference()

const target = ref<IngredientAt | null>(null)

/**
 * The household measures that could weigh the open ingredient's unit — `1 cdta`
 * of this oil — published or the diet's own, best first. Offered, never
 * attached from here without a click.
 */
const measures = shallowRef<FoodMeasure[]>([])
const loadingMeasures = ref(false)
const savingMeasure = ref(false)
const half = ref<CatalogueHalf>('bedca')
const term = ref('')
const results = ref<CatalogueResult[]>([])
const searching = ref(false)
const linking = ref(false)
const error = ref<string | null>(null)

/**
 * The ranked candidates the backend attached to each unmatched ingredient,
 * keyed by ingredient id. One request covers a whole week and costs no query
 * per ingredient — the suggestion index is held in memory over 957 names.
 */
const suggestions = reactive<Record<number, FoodSuggestion[]>>({})
const suggestionsFor = ref<number | null>(null)
const loadingSuggestions = ref(false)

let searchTimer: ReturnType<typeof setTimeout> | undefined
let searchToken = 0

const open = computed(() => target.value !== null)

/** The candidates for the open ingredient, ranked, before anything is typed. */
const ranked = computed<CatalogueResult[]>(() => {
  const at = target.value
  if (!at) {
    return []
  }
  return (suggestions[at.id] ?? []).map((suggestion) => ({
    key: `bedca-${suggestion.bedcaFoodId}`,
    bedcaFoodId: suggestion.bedcaFoodId,
    foodItemId: null,
    name: suggestion.name,
    note: suggestion.foodGroup,
    code: null,
    kcalPer100: null,
    score: suggestion.score,
  }))
})

/** How many of the week's ingredients are still waiting, the open one included. */
const remaining = computed(() => draft.unmatched.value.length)

const position = computed(() => {
  const at = target.value
  if (!at) {
    return 0
  }
  const index = draft.unmatched.value.findIndex((candidate) => candidate.id === at.id)
  return index === -1 ? 0 : index + 1
})

function bedcaResult(food: BedcaFood): CatalogueResult {
  return {
    key: `bedca-${food.id}`,
    bedcaFoodId: food.id,
    foodItemId: null,
    name: food.name,
    note: food.foodGroup,
    code: null,
    kcalPer100: food.nutrition?.energyKcal ?? null,
    score: null,
  }
}

function brandedResult(item: FoodItem): CatalogueResult {
  return {
    key: `item-${item.id}`,
    bedcaFoodId: null,
    foodItemId: item.id,
    name: item.commercialName ?? item.legalName ?? `EAN ${item.ean ?? item.id}`,
    note: item.brand,
    code: item.ean,
    kcalPer100: item.energyKcal,
    score: null,
  }
}

async function runSearch(query: string): Promise<void> {
  const token = ++searchToken
  searching.value = true
  error.value = null
  try {
    const page =
      half.value === 'bedca'
        ? (await catalogueApi.bedca(query, 0, SEARCH_SIZE)).content.map(bedcaResult)
        : (await catalogueApi.branded(query, 0, SEARCH_SIZE)).content.map(brandedResult)
    // The term may have moved on, or the drawer closed, while this was in flight.
    if (token === searchToken) {
      results.value = page
    }
  } catch (cause) {
    if (token === searchToken) {
      results.value = []
      error.value = `No se pudo buscar en el catálogo${
        cause instanceof Error ? `: ${cause.message}` : ''
      }`
    }
  } finally {
    if (token === searchToken) {
      searching.value = false
    }
  }
}

function clearSearchTimer(): void {
  if (searchTimer !== undefined) {
    clearTimeout(searchTimer)
    searchTimer = undefined
  }
}

/** An empty box is not an empty result: it is the ranked candidates again. */
function search(query: string): void {
  term.value = query
  clearSearchTimer()
  if (query.trim() === '') {
    searchToken++
    results.value = []
    searching.value = false
    return
  }
  searching.value = true
  searchTimer = setTimeout(() => {
    searchTimer = undefined
    void runSearch(query.trim())
  }, SEARCH_DEBOUNCE_MS)
}

function useHalf(which: CatalogueHalf): void {
  if (which === half.value) {
    return
  }
  half.value = which
  results.value = []
  if (term.value.trim() !== '') {
    search(term.value)
  }
}

/**
 * The week's unmatched ingredients with their candidates, in one request.
 * Refetched when the week has been replaced, because the rows — and so the ids
 * these are keyed by — are written afresh by every publish.
 */
async function loadSuggestions(): Promise<void> {
  const plan = draft.diet.value
  const version = draft.weekVersion.value
  if (!plan || loadingSuggestions.value || suggestionsFor.value === version) {
    return
  }
  loadingSuggestions.value = true
  try {
    const page = await dietsApi.ingredients(plan.id, {
      resolved: false,
      suggest: true,
      size: SUGGESTION_PAGE,
    })
    for (const key of Object.keys(suggestions)) {
      delete suggestions[Number(key)]
    }
    for (const ingredient of page.content) {
      if (ingredient.id !== null) {
        suggestions[ingredient.id] = ingredient.suggestions ?? []
      }
    }
    suggestionsFor.value = version
  } catch {
    // A missing suggestion list is a shorter drawer, not a broken one: the
    // search box below it answers the same question by hand.
    suggestionsFor.value = null
  } finally {
    loadingSuggestions.value = false
  }
}

/** The household measure the open ingredient is written in, or null for a weight, a volume or an unknown word. */
const measureWord = computed<HouseholdMeasure | null>(() =>
  reference.measureOfUnit(target.value?.unit),
)

/** A generic food written in a household measure: the only kind a measure can weigh. */
const measurable = computed(
  () => target.value !== null && target.value.bedcaFoodId !== null && measureWord.value !== null,
)

let measureToken = 0

async function loadMeasures(): Promise<void> {
  const at = target.value
  const mine = ++measureToken
  measures.value = []
  if (!at || at.bedcaFoodId === null) {
    return
  }
  await reference.ensure().catch(() => undefined)
  if (reference.measureOfUnit(at.unit) === null) {
    return
  }
  loadingMeasures.value = true
  try {
    const rows = await referenceApi.measures({ bedcaFoodId: at.bedcaFoodId }, {
      unit: at.unit,
      dietId: draft.diet.value?.id,
      profile: draft.diet.value?.referenceProfileCode,
    })
    if (mine === measureToken) {
      measures.value = rows
    }
  } catch {
    // No candidates is a shorter drawer: the diet's own criterion below still works.
  } finally {
    if (mine === measureToken) {
      loadingMeasures.value = false
    }
  }
}

/** Opens the drawer on one ingredient, moving the grid to the cell it sits in. */
function focus(ingredient: IngredientAt): void {
  target.value = ingredient
  draft.select(ingredient.row, ingredient.day)
  clearSearchTimer()
  searchToken++
  term.value = ''
  results.value = []
  searching.value = false
  error.value = null
  void loadSuggestions()
  void loadMeasures()
}

/** Weighs the open ingredient through one measure. Saved at once, like a match. */
async function pickMeasure(measure: FoodMeasure): Promise<void> {
  const at = target.value
  const plan = draft.diet.value
  if (!at || !plan || savingMeasure.value || measure.gramsPerMeasure === null) {
    return
  }
  savingMeasure.value = true
  error.value = null
  try {
    const updated = await dietsApi.resolveIngredient(plan.id, at.id, { foodMeasureId: measure.id })
    draft.applyIngredient(updated)
    target.value = locate(at.row, at.day, updated)
  } catch (cause) {
    error.value = `No se pudo usar esa medida${cause instanceof Error ? `: ${cause.message}` : ''}`
  } finally {
    savingMeasure.value = false
  }
}

/**
 * Settles a quantity written as a range (`40-60 gr`) on one value. Until then
 * the ingredient counts in no total: either end, or the middle, would be a
 * choice the text did not make. The cell's sentence is left as written.
 */
async function confirmQuantity(value: number): Promise<void> {
  const at = target.value
  const plan = draft.diet.value
  if (!at || !plan || savingMeasure.value || !(value > 0)) {
    return
  }
  savingMeasure.value = true
  error.value = null
  try {
    const updated = await dietsApi.resolveIngredient(plan.id, at.id, { quantity: value })
    draft.applyIngredient(updated)
    target.value = locate(at.row, at.day, updated)
  } catch (cause) {
    error.value = `No se pudo fijar la cantidad${cause instanceof Error ? `: ${cause.message}` : ''}`
  } finally {
    savingMeasure.value = false
  }
}

/** Starts the review at the first ingredient of the week still waiting. */
function review(): boolean {
  const first = draft.unmatched.value[0]
  if (!first) {
    return false
  }
  focus(first)
  return true
}

function close(): void {
  clearSearchTimer()
  searchToken++
  measureToken++
  measures.value = []
  target.value = null
  term.value = ''
  results.value = []
  searching.value = false
  error.value = null
}

/**
 * Moves to the next ingredient waiting after the one just matched. The queue it
 * walks has already dropped that ingredient, so the entry now standing at the
 * same position is the next one; at the end of the queue the drawer closes.
 */
function advance(from: number): void {
  const waiting = draft.unmatched.value
  const next = waiting[Math.min(from, waiting.length - 1)]
  if (next) {
    focus(next)
  } else {
    close()
  }
}

/** Leaves this ingredient as it is and moves to the next one waiting. */
function skip(): void {
  const from = position.value
  const next = from === 0 ? undefined : draft.unmatched.value[from]
  if (next) {
    focus(next)
  } else {
    close()
  }
}

/** Writes the match, then moves on to the next one waiting. */
async function link(result: CatalogueResult): Promise<void> {
  const at = target.value
  if (!at || linking.value) {
    return
  }
  // Where in the queue this ingredient stood, so the next one can be found once
  // it has left it. Zero means it was never in the queue — a match already made
  // and being corrected — and correcting one is not a walk through the week.
  const from = position.value
  linking.value = true
  error.value = null
  try {
    const updated: DishIngredient = await dietsApi.resolveIngredient(
      draft.diet.value!.id,
      at.id,
      result.bedcaFoodId !== null
        ? { bedcaFoodId: result.bedcaFoodId }
        : { foodItemId: result.foodItemId },
    )
    draft.applyIngredient(updated)
    delete suggestions[at.id]
    // Matched but still weighing nothing — `1 cdta` of an oil no source
    // measures — so the drawer stays on it for its measure rather than moving on.
    if (isUnweighed(updated) && updated.bedcaFoodId !== null && reference.measureOfUnit(updated.unit)) {
      const matched = locate(at.row, at.day, updated)
      if (matched) {
        target.value = matched
        void loadMeasures()
        return
      }
    }
    if (from === 0) {
      close()
    } else {
      advance(from - 1)
    }
  } catch (cause) {
    error.value = `No se pudo vincular el ingrediente${
      cause instanceof Error ? `: ${cause.message}` : ''
    }`
  } finally {
    linking.value = false
  }
}

export function useFoodLink() {
  return {
    // state
    target,
    half,
    term,
    results,
    searching,
    linking,
    error,
    loadingSuggestions,
    measures,
    loadingMeasures,
    savingMeasure,
    // derived
    open,
    ranked,
    remaining,
    position,
    measureWord,
    measurable,
    // writes
    focus,
    pickMeasure,
    confirmQuantity,
    loadMeasures,
    review,
    close,
    skip,
    search,
    useHalf,
    link,
    loadSuggestions,
  }
}
