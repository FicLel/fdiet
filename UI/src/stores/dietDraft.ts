import { computed, reactive, ref, shallowRef, triggerRef } from 'vue'
import { ApiError } from '@/api/http'
import {
  dietsApi,
  type DietRequest,
  type KeptMatch,
  type DietSettings,
  type ImportDietRequest,
  type RequestDish,
  type RequestMeal,
} from '@/api/diets'
import { usePatients } from '@/stores/patients'
import type {
  DayOfWeek,
  Diet,
  DietImportSummary,
  Dish,
  DishIngredient,
  MealType,
  Recipe,
} from '@/api/types'
import { buildRows, cellKey, dishAt, mealOf, type GridRow, type MealRow } from '@/domain/slots'
import { joinFragment, renderRecipe } from '@/domain/dishText'
import { dishTotals, ingredientsOf, sumTotals, type DishTotals } from '@/domain/nutrition'
import { locate, type IngredientAt } from '@/domain/ingredientAt'
import { droppedFrom, matchesOf, toSend } from '@/domain/keptMatches'
import { recipeRequest } from '@/domain/requestIngredient'
import { addDays, dayName, dayNumber, longDate, mondayOf, WEEK } from '@/domain/week'

// Where an ingredient sits moved to `domain/ingredientAt`; its callers still reach it here.
export { isUnweighed, locate, type IngredientAt } from '@/domain/ingredientAt'

/**
 * The week the nutritionist is working on, and the changes not yet published.
 *
 * **Nothing reaches the patient until it is published.** An edit lives here as
 * the text that was typed plus the dish the backend read out of it; the stored
 * diet is left exactly as it was until `publish` writes the whole week.
 *
 * **One patient at a time.** Which one is not held here — it is the selection
 * in `stores/patients`, shared with the patient's own screen so the two are
 * never looking at different people. Switching patient throws the draft away
 * and loads theirs: an unpublished edit belongs to the week it was typed into,
 * and carrying it across would write one person's breakfast into another's.
 *
 * The kcal a cell shows are worked out here rather than taken from the diet's
 * own summaries, because a figure has to move as the text is typed — and it is
 * the same arithmetic either way, over the same per-ingredient figures.
 */

/** How long a pause in the typing means the cell is ready to be read. */
const PARSE_DEBOUNCE_MS = 450

/**
 * Placeholder. The backend carries no goal — a patient row is a name and a
 * note, nothing about what they should be eating — so the goal line is the
 * nutritionist's own, held in the browser and never sent. It is also the same
 * one for everybody, which is the honest consequence: a goal is per patient and
 * this is not stored per patient.
 */
const PLACEHOLDER_TARGET_KCAL = 1900

/**
 * Where a plate's food comes from: a recipe of the shared library (linked, so it
 * changes with the library), one written for this plate alone, or none — a plate
 * that is a description only ("Comida libre").
 */
export type RecipeMode = 'library' | 'own' | 'none'

export interface CellEdit {
  /** The description the patient reads. Nothing reads food out of it. */
  name: string
  servings: number
  mode: RecipeMode
  /** The plate's own recipe as typed: its ingredients. */
  text: string
  /** The plate's own recipe: how it is made. */
  steps: string
  /**
   * The recipe the plate shows: the library one it links, or what the backend
   * read out of `text`. While a new text is being read, the last one known.
   */
  recipe: Recipe | null
  /**
   * The plate's own stored recipe, while nothing about it has been touched: the
   * publish then names it by id and the backend keeps it — matches and all —
   * rather than writing it again.
   */
  keepRecipeId: number | null
  /** The matches the next read of `text` keeps by name (FD-048): stored, read or composed. */
  keep: KeptMatch[]
  parsing: boolean
  /** The backend could not read the text; the cell keeps its old figures. */
  failed: boolean
}

export interface DayColumn {
  day: DayOfWeek
  name: string
  /** The day of the month alone; the grid header has no room for more. */
  date: string
  longDate: string
  totals: DishTotals
}

const diet = shallowRef<Diet | null>(null)
const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
/** The selected patient has no diet in force — not a failure, a week still to be written. */
const missing = ref(false)
const publishing = ref(false)
const error = ref<string | null>(null)
const edits = reactive<Record<string, CellEdit>>({})
const selection = ref<{ day: DayOfWeek; mealType: MealType; dishIndex: number } | null>(null)

/**
 * Bumped whenever the stored week is replaced wholesale — a load, or a publish.
 * The rows are written afresh each time, so every ingredient id from before it
 * is gone; anything holding ids against a week compares this and refetches.
 */
const weekVersion = ref(0)

/** Placeholders, browser-only, until the backend carries a goal per patient. */
const goalNote = ref('1.900 kcal/día · 120 g de proteína o más')
const targetKcal = ref(PLACEHOLDER_TARGET_KCAL)

const parseTimers = new Map<string, ReturnType<typeof setTimeout>>()
/** Per cell, the matches its reads dropped (FD-056): sent again until publish or discard. */
const dropped = new Map<string, KeptMatch[]>()
const sendFor = (key: string, keep: KeptMatch[]) => toSend(keep, dropped.get(key) ?? [])

const rows = computed<GridRow[]>(() =>
  diet.value ? buildRows(diet.value, { template: true }) : [],
)

const mealRows = computed<MealRow[]>(() =>
  rows.value.filter((row): row is MealRow => row.kind === 'meal'),
)

/**
 * Every day of the week, Monday first, dated off `startedOn`. The editor offers
 * all seven whatever the diet fills: a new week starts with none of them, and a
 * day nobody has written is a column waiting, not one that does not exist.
 */
const days = computed<DayColumn[]>(() => {
  const plan = diet.value
  if (!plan) {
    return []
  }
  const first = mondayOf(plan.startedOn)
  return WEEK.map((day) => {
    const date = addDays(first, WEEK.indexOf(day))
    return {
      day,
      name: dayName(day),
      date: dayNumber(date),
      longDate: longDate(date),
      totals: dayTotals(day),
    }
  })
})

const monday = computed(() => (diet.value ? mondayOf(diet.value.startedOn) : new Date()))

const dirtyCount = computed(() => Object.keys(edits).length)

const weekAverageKcal = computed(() => {
  // Over the days that hold something: an empty column is a day not written
  // yet, not a day of fasting.
  const columns = days.value.filter((column) => column.totals.kcal !== null)
  if (columns.length === 0) {
    return null
  }
  const total = columns.reduce((sum, column) => sum + (column.totals.kcal ?? 0), 0)
  return total / columns.length
})

function storedDish(day: DayOfWeek, mealType: MealType, dishIndex: number): Dish | undefined {
  return dishAt(
    diet.value?.days.find((candidate) => candidate.day === day),
    mealType,
    dishIndex,
  )
}

/** The dish a cell shows: the one being edited, else the stored one. */
function dishFor(day: DayOfWeek, mealType: MealType, dishIndex: number): Dish | undefined {
  const edit = edits[`${day}|${mealType}|${dishIndex}`]
  if (!edit) {
    return storedDish(day, mealType, dishIndex)
  }
  if (edit.name.trim() === '' && edit.mode === 'none') {
    return undefined
  }
  const recipe = edit.mode === 'none' ? null : edit.recipe
  return {
    name: edit.name,
    servings: edit.servings,
    recipeId: recipe?.id ?? null,
    // The steps are typed here and never read by the backend, so they are the
    // edit's own rather than whatever the last parse answered with.
    recipe: recipe && edit.mode === 'own' ? { ...recipe, steps: edit.steps || null } : recipe,
    nutrition: null,
  }
}

/** What the cell reads: the description the patient will see. */
function textFor(row: MealRow, day: DayOfWeek): string {
  const edit = edits[cellKey(row, day)]
  return edit ? edit.name : (storedDish(day, row.mealType, row.dishIndex)?.name ?? '')
}

/** The plate's own recipe as typed, or its stored text. Empty for a library recipe. */
function recipeTextFor(row: MealRow, day: DayOfWeek): string {
  const edit = edits[cellKey(row, day)]
  if (edit) {
    return edit.text
  }
  const recipe = storedDish(day, row.mealType, row.dishIndex)?.recipe
  return recipe && !recipe.library ? renderRecipe(recipe) : ''
}

function totalsFor(row: MealRow, day: DayOfWeek): DishTotals {
  return dishTotals(dishFor(day, row.mealType, row.dishIndex))
}

function dayTotals(day: DayOfWeek): DishTotals {
  return sumTotals(mealRows.value.map((row) => totalsFor(row, day)))
}

function isEdited(row: MealRow, day: DayOfWeek): boolean {
  return edits[cellKey(row, day)] !== undefined
}

/**
 * Every ingredient of the week still waiting to be matched, in the order the
 * grid reads: along a slot's row across the days, then down to the next slot.
 *
 * A cell with unpublished changes is skipped. Its stored ingredients are about
 * to be replaced by the publish, so matching one of them would be work thrown
 * away without anything saying so.
 */
const unmatched = computed<IngredientAt[]>(() => {
  const waiting: IngredientAt[] = []
  // A library recipe served in several cells is one set of rows: listed once.
  const seen = new Set<number>()
  for (const row of mealRows.value) {
    for (const column of days.value) {
      if (edits[cellKey(row, column.day)]) {
        continue
      }
      const dish = storedDish(column.day, row.mealType, row.dishIndex)
      for (const ingredient of ingredientsOf(dish)) {
        if (ingredient.foodItemId !== null || ingredient.compositionFoodId !== null) {
          continue
        }
        const at = locate(row, column.day, ingredient)
        if (at && !seen.has(at.id)) {
          seen.add(at.id)
          waiting.push(at)
        }
      }
    }
  }
  return waiting
})

/**
 * Puts one ingredient back where it came from, after a PATCH matched it to a
 * food. The whole week is not re-fetched: the backend hands the corrected
 * ingredient back with its matched name and its own scaled figures, which is
 * everything that changed.
 */
function applyIngredient(updated: DishIngredient): boolean {
  const plan = diet.value
  if (!plan || updated.id === null) {
    return false
  }
  // A library recipe may be served in several cells, each holding its own copy
  // of the same row: every one of them changes.
  let found = false
  for (const day of plan.days) {
    for (const meal of day.meals) {
      for (const dish of meal.dishes) {
        const ingredients = ingredientsOf(dish)
        const at = ingredients.findIndex((candidate) => candidate.id === updated.id)
        if (at !== -1) {
          ingredients[at] = updated
          found = true
        }
      }
    }
  }
  if (found) {
    // `diet` is a shallowRef — the week arrives whole and is replaced whole, so
    // nothing under it is tracked. One ingredient changing beneath it is the
    // exception, and this is it saying so.
    triggerRef(diet)
  }
  return found
}

/** A stored ingredient by its row id, with the cell it sits in. */
function findIngredient(id: number): IngredientAt | null {
  for (const row of mealRows.value) {
    for (const column of days.value) {
      const dish = storedDish(column.day, row.mealType, row.dishIndex)
      const found = ingredientsOf(dish).find((ingredient) => ingredient.id === id)
      if (found) {
        return locate(row, column.day, found)
      }
    }
  }
  return null
}

function selectFirstCell(): void {
  const row = mealRows.value[0]
  const day = days.value[0]
  selection.value =
    row && day ? { day: day.day, mealType: row.mealType, dishIndex: row.dishIndex } : null
}

/**
 * Loads the selected patient's week, throwing away whatever was being edited.
 * An unpublished edit belongs to the week it was typed into: keeping it across
 * a switch would put one patient's breakfast into another patient's day.
 */
async function load(): Promise<void> {
  const patients = usePatients()
  const patientId = patients.selectedId.value
  discardAll()
  missing.value = false
  if (patientId === null) {
    // Nobody to write a diet for yet. Not an error — the very first thing the
    // editor does is add somebody.
    diet.value = null
    error.value = 'Añade un paciente para empezar a escribirle una dieta.'
    status.value = 'error'
    return
  }
  status.value = 'loading'
  error.value = null
  try {
    diet.value = await dietsApi.active(patientId)
    weekVersion.value++
    status.value = 'ready'
    selectFirstCell()
  } catch (cause) {
    diet.value = null
    const who = patients.selected.value?.name ?? 'Este paciente'
    missing.value = cause instanceof ApiError && cause.status === 404
    error.value = missing.value
      ? `${who} todavía no tiene ninguna dieta. Empieza una semana en blanco, impórtala desde un libro de Excel o copia la de otro paciente.`
      : `No se pudo cargar la dieta${cause instanceof Error ? `: ${cause.message}` : ''}`
    status.value = 'error'
  }
}

/**
 * Reads the stored week again without touching what is being typed — for a
 * change made beside the week, such as a measure criterion attached to several
 * ingredients at once. The edits still belong to the same cells.
 */
async function refresh(): Promise<void> {
  const plan = diet.value
  if (!plan) {
    return
  }
  try {
    diet.value = await dietsApi.byId(plan.id)
    weekVersion.value++
  } catch (cause) {
    error.value = `No se pudo volver a leer la dieta${
      cause instanceof Error ? `: ${cause.message}` : ''
    }`
  }
}

function select(row: MealRow, day: DayOfWeek): void {
  selection.value = { day, mealType: row.mealType, dishIndex: row.dishIndex }
}

const selectedRow = computed<MealRow | null>(() => {
  const at = selection.value
  if (!at) {
    return null
  }
  return (
    mealRows.value.find((row) => row.mealType === at.mealType && row.dishIndex === at.dishIndex) ??
    null
  )
})

const selectedDay = computed<DayColumn | null>(() => {
  const at = selection.value
  return at ? (days.value.find((column) => column.day === at.day) ?? null) : null
})

function clearTimer(key: string): void {
  const timer = parseTimers.get(key)
  if (timer !== undefined) {
    clearTimeout(timer)
    parseTimers.delete(key)
  }
}

async function reparse(key: string, slotName: string): Promise<void> {
  parseTimers.delete(key)
  const edit = edits[key]
  if (!edit || edit.mode !== 'own' || edit.text.trim() === '') {
    return
  }
  const text = edit.text
  const keep = sendFor(key, edit.keep)
  try {
    const recipe = await dietsApi.parse({
      text,
      slotName: edit.name.trim() || slotName,
      dietId: diet.value?.id,
      keep,
    })
    // The text may have moved on while the request was in flight.
    const current = edits[key]
    if (current?.mode === 'own' && current.text === text) {
      current.recipe = recipe
      // What the answer holds matched is what the next read keeps: a name gone
      // from the text is gone from the answer, and its match with it.
      current.keep = matchesOf(recipe.ingredients)
      dropped.set(key, droppedFrom(keep, current.keep))
      // "Ensalada: lechuga (80 gr)" names its plate; a description nobody has
      // written yet takes that name rather than staying blank.
      if (current.name.trim() === '' && recipe.name !== slotName) {
        current.name = recipe.name
      }
      current.parsing = false
      current.failed = false
    }
  } catch {
    const current = edits[key]
    if (current?.mode === 'own' && current.text === text) {
      current.parsing = false
      current.failed = true
    }
  }
}

/**
 * The cell's edit, opened from what is stored the first time the cell is
 * touched. A stored private recipe starts out kept by id, so changing only the
 * description or the servings writes nothing of the recipe again.
 */
function editOf(row: MealRow, day: DayOfWeek): CellEdit {
  const key = cellKey(row, day)
  const existing = edits[key]
  if (existing) {
    return existing
  }
  const stored = storedDish(day, row.mealType, row.dishIndex)
  const recipe = stored?.recipe ?? null
  const own = recipe !== null && !recipe.library
  edits[key] = {
    name: stored?.name ?? '',
    servings: stored?.servings ?? 1,
    mode: recipe === null ? (stored ? 'none' : 'own') : recipe.library ? 'library' : 'own',
    text: own ? renderRecipe(recipe) : '',
    steps: own ? (recipe.steps ?? '') : '',
    recipe,
    keepRecipeId: own ? recipe.id : null,
    keep: own ? matchesOf(recipe.ingredients) : [],
    parsing: false,
    failed: false,
  }
  return edits[key]
}

/**
 * An edit that has come back round to what is stored is no edit at all, so the
 * cell goes back to being clean rather than standing marked for no difference.
 */
function settle(row: MealRow, day: DayOfWeek): void {
  const key = cellKey(row, day)
  const edit = edits[key]
  if (!edit || edit.parsing) {
    return
  }
  const stored = storedDish(day, row.mealType, row.dishIndex)
  const sameRecipe =
    edit.mode === 'library'
      ? edit.recipe?.id === stored?.recipeId
      : edit.mode === 'own'
        ? edit.keepRecipeId !== null
          ? edit.keepRecipeId === stored?.recipeId
          : !stored && edit.text.trim() === ''
        : (stored?.recipe ?? null) === null
  const sameName = edit.name.trim() === (stored?.name ?? '').trim()
  const sameServings = edit.servings === (stored?.servings ?? 1)
  if (sameRecipe && sameName && sameServings) {
    clearTimer(key)
    delete edits[key]
  }
}

function setName(row: MealRow, day: DayOfWeek, name: string): void {
  editOf(row, day).name = name
  settle(row, day)
}

function setServings(row: MealRow, day: DayOfWeek, servings: number): void {
  if (!Number.isFinite(servings) || servings <= 0) {
    return
  }
  editOf(row, day).servings = servings
  settle(row, day)
}

/** Holds the typed recipe text and asks the backend to read it once the typing pauses. */
function setRecipeText(row: MealRow, day: DayOfWeek, text: string): void {
  const key = cellKey(row, day)
  const edit = editOf(row, day)
  clearTimer(key)
  const stored = storedDish(day, row.mealType, row.dishIndex)
  if (edit.keepRecipeId !== null && edit.keepRecipeId === stored?.recipeId) {
    // The text was still the stored one: carry what is stored now, which a PATCH
    // or a refresh may have matched differently since the edit opened.
    edit.keep = matchesOf(ingredientsOf(stored))
  }
  edit.mode = 'own'
  edit.text = text
  edit.keepRecipeId = null
  edit.failed = false
  if (text.trim() === '') {
    // No ingredients written: nothing to read, nothing on the plate, nothing kept.
    edit.recipe = null
    dropped.set(key, droppedFrom(sendFor(key, edit.keep), []))
    edit.keep = []
    edit.parsing = false
    settle(row, day)
    return
  }
  edit.parsing = true
  parseTimers.set(
    key,
    setTimeout(() => {
      void reparse(key, row.label)
    }, PARSE_DEBOUNCE_MS),
  )
}

function setSteps(row: MealRow, day: DayOfWeek, steps: string): void {
  const edit = editOf(row, day)
  edit.mode = 'own'
  edit.steps = steps
  // The recipe is written again on publish, with the ingredients already read —
  // and the matches they carry.
  edit.keepRecipeId = null
}

/**
 * Points the plate at a library recipe. It is linked, not copied: the plate
 * reads whatever the library holds, now and after every later edit to it.
 */
function useLibraryRecipe(row: MealRow, day: DayOfWeek, recipe: Recipe): void {
  const key = cellKey(row, day)
  const edit = editOf(row, day)
  clearTimer(key)
  edit.mode = 'library'
  edit.recipe = recipe
  edit.keepRecipeId = null
  edit.parsing = false
  edit.failed = false
  if (edit.name.trim() === '') {
    edit.name = recipe.name
  }
  settle(row, day)
}

/**
 * Takes a copy of the linked library recipe as the plate's own, to change it for
 * this plate alone. The matches come with it; the library is not touched.
 */
function detachRecipe(row: MealRow, day: DayOfWeek): void {
  const edit = editOf(row, day)
  const recipe = edit.recipe
  if (edit.mode !== 'library' || !recipe) {
    return
  }
  edit.mode = 'own'
  edit.text = renderRecipe(recipe)
  edit.steps = recipe.steps ?? ''
  edit.recipe = { ...recipe, id: null, library: false }
  edit.keep = matchesOf(recipe.ingredients)
  edit.keepRecipeId = null
}

/** A plate that is a description only. */
function clearRecipe(row: MealRow, day: DayOfWeek): void {
  const key = cellKey(row, day)
  const edit = editOf(row, day)
  clearTimer(key)
  edit.mode = 'none'
  edit.recipe = null
  dropped.set(key, droppedFrom(sendFor(key, edit.keep), []))
  edit.keep = []
  edit.keepRecipeId = null
  edit.parsing = false
  settle(row, day)
}

/** Takes the plate out of the week: no description and nothing on it. */
function removeDish(row: MealRow, day: DayOfWeek): void {
  const edit = editOf(row, day)
  edit.name = ''
  clearRecipe(row, day)
}

/**
 * Appends a composed food to the plate's own recipe. The text is the one the
 * backend wrote for it, so the recipe text stays the only source of truth and is
 * read back by the same parser as anything typed — keeping the food the composer
 * pinned, which its name alone might not match.
 */
function appendFragment(row: MealRow, day: DayOfWeek, fragment: string, pin: DishIngredient): void {
  const edit = editOf(row, day)
  const own = edit.mode === 'own'
  setRecipeText(row, day, joinFragment(own ? edit.text : '', fragment))
  // After the text is set, which may have re-seeded what an own recipe keeps.
  edit.keep = [...(own ? edit.keep : []), ...matchesOf([pin])]
}

/** Puts one cell back the way it is stored. */
function revert(row: MealRow, day: DayOfWeek): void {
  const key = cellKey(row, day)
  clearTimer(key)
  dropped.delete(key)
  delete edits[key]
}

function discardAll(): void {
  for (const key of Object.keys(edits)) {
    clearTimer(key)
    delete edits[key]
  }
  dropped.clear()
}

/** Reads any recipe still waiting on its debounce, so nothing is published stale. */
async function settleParses(): Promise<void> {
  const pending: Promise<void>[] = []
  for (const row of mealRows.value) {
    for (const column of days.value) {
      const key = cellKey(row, column.day)
      const edit = edits[key]
      if (edit?.mode === 'own' && edit.text.trim() !== '' && edit.parsing) {
        clearTimer(key)
        pending.push(reparse(key, row.label))
      }
    }
  }
  await Promise.all(pending)
}

/**
 * One cell as the publish sends it. A cell nobody touched names its recipe by
 * id and the backend keeps it as it is; an edited one names the library recipe
 * it links, or sends its own recipe written out. A plate with no description
 * left is not sent: it has been taken out of the week.
 */
function dishRequest(day: DayOfWeek, row: MealRow): RequestDish | null {
  const edit = edits[cellKey(row, day)]
  if (!edit) {
    const stored = storedDish(day, row.mealType, row.dishIndex)
    return stored
      ? { name: stored.name, servings: stored.servings, recipeId: stored.recipeId }
      : null
  }
  const name = edit.name.trim() || edit.recipe?.name?.trim() || ''
  if (name === '') {
    return null
  }
  const plate = { name, servings: edit.servings }
  if (edit.mode === 'library' && edit.recipe?.id != null) {
    return { ...plate, recipeId: edit.recipe.id }
  }
  if (edit.mode === 'own') {
    if (edit.keepRecipeId !== null) {
      return { ...plate, recipeId: edit.keepRecipeId }
    }
    const recipe = recipeRequest(edit.text, edit.steps, edit.recipe?.ingredients ?? [])
    return recipe ? { ...plate, recipe } : plate
  }
  return plate
}

/**
 * A meal's dishes in row order. A removed plate drops out, and a plate written
 * where the day had none is appended — the rows below do not shift, because a
 * day that never filled the last row simply has fewer dishes.
 */
function dishesRequest(day: DayOfWeek, mealType: MealType): RequestDish[] {
  const dishes: RequestDish[] = []
  for (const row of mealRows.value.filter((candidate) => candidate.mealType === mealType)) {
    const dish = dishRequest(day, row)
    if (dish) {
      dishes.push(dish)
    }
  }
  return dishes
}

function mealsRequest(plan: Diet, day: DayOfWeek): RequestMeal[] {
  const stored = plan.days.find((candidate) => candidate.day === day)
  const meals: RequestMeal[] = []
  // One request meal per meal slot; the rows of that slot become its dishes.
  for (const row of mealRows.value.filter((candidate) => candidate.dishIndex === 0)) {
    const dishes = dishesRequest(day, row.mealType)
    if (dishes.length === 0) {
      continue
    }
    const meal = mealOf(stored, row.mealType)
    meals.push({ type: row.mealType, name: meal?.name || row.sub || row.label, dishes })
  }
  return meals
}

function weekRequest(plan: Diet): DietRequest {
  return {
    // The patient the diet already belongs to. A diet does not change hands
    // through an edit — the backend refuses it — because moving one would take
    // the patient's journal with it. `copyTo` is how a week reaches somebody
    // else, and it leaves this one where it is.
    patientId: plan.patientId,
    name: plan.name,
    startedOn: plan.startedOn,
    referenceProfileCode: plan.referenceProfileCode,
    clinical: plan.clinical,
    days: days.value
      .map((column) => ({ day: column.day as string, meals: mealsRequest(plan, column.day) }))
      .filter((day) => day.meals.length > 0),
  }
}

/**
 * Writes the week. `PUT /api/diets/{id}` replaces it wholesale, so every day is
 * sent, edited or not, and each ingredient carries the food id it was matched
 * to — a match made by hand would otherwise be lost to a re-read of the name.
 */
async function publish(): Promise<void> {
  const plan = diet.value
  if (!plan || dirtyCount.value === 0 || publishing.value) {
    return
  }
  publishing.value = true
  error.value = null
  try {
    await settleParses()
    diet.value = await dietsApi.update(plan.id, weekRequest(plan))
    weekVersion.value++
    discardAll()
    // The selector names each patient's diet, and this publish may have renamed
    // one of them.
    void usePatients().refreshCurrent()
  } catch (cause) {
    error.value = `No se pudieron publicar los cambios${
      cause instanceof Error ? `: ${cause.message}` : ''
    }`
  } finally {
    publishing.value = false
  }
}

/**
 * Writes this week again for another patient.
 *
 * Unpublished edits are deliberately not part of it. The backend copies what is
 * *stored*, and a copy that quietly included changes nobody had published would
 * put a week into somebody else's record that the nutritionist has not agreed
 * to yet — so the caller is told to publish first rather than being surprised
 * afterwards.
 *
 * The copy takes the target's active slot: whatever they were on is archived,
 * exactly as it is when a diet is imported for them.
 */
async function copyTo(patientId: number, name?: string): Promise<boolean> {
  const plan = diet.value
  if (!plan || publishing.value) {
    return false
  }
  if (dirtyCount.value > 0) {
    error.value =
      'Publica o descarta los cambios antes de copiar la semana: se copia lo que está guardado.'
    return false
  }
  publishing.value = true
  error.value = null
  try {
    await dietsApi.copy(plan.id, { patientId, name: name ?? null })
    await usePatients().refreshCurrent()
    return true
  } catch (cause) {
    error.value = `No se pudo copiar la dieta${
      cause instanceof Error ? `: ${cause.message}` : ''
    }`
    return false
  } finally {
    publishing.value = false
  }
}

function failure(what: string, cause: unknown): string {
  return `${what}${cause instanceof Error ? `: ${cause.message}` : ''}`
}

/**
 * Changes what the week is read against. It never rewrites a gram: the profile
 * decides what the counts are compared with, not what the diet says.
 */
async function updateSettings(settings: DietSettings): Promise<boolean> {
  const plan = diet.value
  if (!plan) {
    return false
  }
  error.value = null
  try {
    const updated = await dietsApi.updateSettings(plan.id, settings)
    // Only the settings moved; the week is the same rows it was, and any cell
    // being edited keeps its draft.
    diet.value = {
      ...plan,
      name: updated.name,
      referenceProfileCode: updated.referenceProfileCode,
      clinical: updated.clinical,
    }
    void usePatients().refreshCurrent()
    return true
  } catch (cause) {
    error.value = failure('No se pudo cambiar la dieta', cause)
    return false
  }
}

/**
 * A new, empty week for the selected patient. It becomes their diet in force and
 * archives the one they were on, exactly as an import does. Answers null when it
 * worked, and what went wrong otherwise.
 */
async function createEmpty(request: {
  name: string
  startedOn: string
  referenceProfileCode: string | null
  clinical: boolean
}): Promise<string | null> {
  const patientId = usePatients().selectedId.value
  if (patientId === null) {
    return 'Elige antes un paciente.'
  }
  if (dirtyCount.value > 0) {
    return 'Publica o descarta antes los cambios: la semana nueva sustituye a la que está en pantalla.'
  }
  try {
    await dietsApi.create({ patientId, days: [], ...request })
    await usePatients().refreshCurrent()
    await load()
    return null
  } catch (cause) {
    return failure('No se pudo crear la dieta', cause)
  }
}

/** Reads a workbook into the selected patient's diet in force. */
async function importWorkbook(
  request: Omit<ImportDietRequest, 'patientId'>,
): Promise<DietImportSummary | string> {
  const patientId = usePatients().selectedId.value
  if (patientId === null) {
    return 'Elige antes un paciente.'
  }
  if (dirtyCount.value > 0) {
    return 'Publica o descarta antes los cambios: la semana importada sustituye a la que está en pantalla.'
  }
  try {
    const summary = await dietsApi.importWorkbook({ ...request, patientId })
    await usePatients().refreshCurrent()
    await load()
    return summary
  } catch (cause) {
    return failure('No se pudo importar el libro', cause)
  }
}

export function useDietDraft() {
  return {
    // state
    diet,
    status,
    missing,
    error,
    publishing,
    edits,
    selection,
    goalNote,
    targetKcal,
    weekVersion,
    // derived
    rows,
    mealRows,
    days,
    monday,
    dirtyCount,
    weekAverageKcal,
    selectedRow,
    selectedDay,
    unmatched,
    // reads
    dishFor,
    storedDish,
    findIngredient,
    textFor,
    recipeTextFor,
    totalsFor,
    isEdited,
    // writes
    load,
    refresh,
    select,
    setName,
    setServings,
    setRecipeText,
    setSteps,
    useLibraryRecipe,
    detachRecipe,
    clearRecipe,
    removeDish,
    revert,
    discardAll,
    publish,
    copyTo,
    applyIngredient,
    appendFragment,
    updateSettings,
    createEmpty,
    importWorkbook,
  }
}
