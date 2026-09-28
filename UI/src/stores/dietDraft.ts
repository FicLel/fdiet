import { computed, reactive, ref, shallowRef, triggerRef } from 'vue'
import { ApiError } from '@/api/http'
import {
  dietsApi,
  type DietRequest,
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
  FoodMeasure,
  FoodState,
  MealType,
  PortionSize,
} from '@/api/types'
import { buildRows, cellKey, dishAt, mealOf, type GridRow, type MealRow } from '@/domain/slots'
import { renderDish } from '@/domain/dishText'
import { dishTotals, type DishTotals } from '@/domain/nutrition'
import { addDays, dayName, dayNumber, longDate, mondayOf, WEEK } from '@/domain/week'

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

export interface CellEdit {
  /** What the nutritionist typed. */
  text: string
  /** What the backend read out of it, once it has answered. */
  dish: Dish | null
  parsing: boolean
  /** The backend could not read the text; the cell keeps its old figures. */
  failed: boolean
}

/**
 * One stored ingredient and the cell it sits in — what the link drawer needs to
 * address it and to show where in the week it is.
 *
 * The id is the stored row's, so only a *stored* ingredient can be one of
 * these. An ingredient read out of a cell being edited has no row of its own
 * yet, and nothing for a PATCH to address; it is matched by publishing first.
 */
export interface IngredientAt {
  /** The stored row's id — what `PATCH …/ingredients/{id}` addresses. */
  id: number
  name: string
  quantity: number
  unit: string
  /** The catalogue's own name for the food, when it is already matched. */
  matchedName: string | null
  bedcaFoodId: number | null
  foodItemId: number | null
  state: FoodState | null
  size: PortionSize | null
  measure: FoodMeasure | null
  /** Matched, but nothing weighs the unit it is written in. */
  unweighed: boolean
  stateMismatch: boolean
  row: MealRow
  day: DayOfWeek
}

/** Matched to a food, and still in no total: nothing weighs the unit it is written in. */
export function isUnweighed(ingredient: DishIngredient): boolean {
  return (
    (ingredient.bedcaFoodId !== null || ingredient.foodItemId !== null) &&
    (ingredient.nutrition === null ||
      Object.values(ingredient.nutrition).every((value) => value === null))
  )
}

/** Where an ingredient of a stored cell sits, or null if it has no row yet. */
export function locate(
  row: MealRow,
  day: DayOfWeek,
  ingredient: DishIngredient,
): IngredientAt | null {
  return ingredient.id === null
    ? null
    : {
        id: ingredient.id,
        name: ingredient.name,
        quantity: ingredient.quantity,
        unit: ingredient.unit,
        matchedName: ingredient.matchedName,
        bedcaFoodId: ingredient.bedcaFoodId,
        foodItemId: ingredient.foodItemId,
        state: ingredient.state,
        size: ingredient.size,
        measure: ingredient.measure,
        unweighed: isUnweighed(ingredient),
        stateMismatch: ingredient.stateMismatch,
        row,
        day,
      }
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

/** The dish a cell shows: the re-read one when it was edited, else the stored one. */
function dishFor(day: DayOfWeek, mealType: MealType, dishIndex: number): Dish | undefined {
  const edit = edits[`${day}|${mealType}|${dishIndex}`]
  if (!edit) {
    return storedDish(day, mealType, dishIndex)
  }
  // While the backend is still reading the new text, the old dish is the last
  // thing actually known about the cell — showing it beats showing nothing.
  return edit.dish ?? storedDish(day, mealType, dishIndex)
}

/** What the cell reads: the typed text, or the stored dish written back out. */
function textFor(row: MealRow, day: DayOfWeek): string {
  const edit = edits[cellKey(row, day)]
  return edit ? edit.text : renderDish(storedDish(day, row.mealType, row.dishIndex), row.label)
}

function totalsFor(row: MealRow, day: DayOfWeek): DishTotals {
  return dishTotals(dishFor(day, row.mealType, row.dishIndex))
}

function plus(total: number | null, value: number | null): number | null {
  return value === null ? total : (total ?? 0) + value
}

function dayTotals(day: DayOfWeek): DishTotals {
  const totals: DishTotals = {
    kcal: null,
    proteinG: null,
    carbohydratesG: null,
    fatG: null,
    ingredients: 0,
    counted: 0,
    unmatched: 0,
    unmeasured: 0,
  }
  for (const row of mealRows.value) {
    const cell = totalsFor(row, day)
    totals.ingredients += cell.ingredients
    totals.counted += cell.counted
    totals.unmatched += cell.unmatched
    totals.unmeasured += cell.unmeasured
    totals.kcal = plus(totals.kcal, cell.kcal)
    totals.proteinG = plus(totals.proteinG, cell.proteinG)
    totals.carbohydratesG = plus(totals.carbohydratesG, cell.carbohydratesG)
    totals.fatG = plus(totals.fatG, cell.fatG)
  }
  return totals
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
  for (const row of mealRows.value) {
    for (const column of days.value) {
      if (edits[cellKey(row, column.day)]) {
        continue
      }
      const dish = storedDish(column.day, row.mealType, row.dishIndex)
      for (const ingredient of dish?.ingredients ?? []) {
        if (ingredient.foodItemId !== null || ingredient.bedcaFoodId !== null) {
          continue
        }
        const at = locate(row, column.day, ingredient)
        if (at) {
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
  for (const day of plan.days) {
    for (const meal of day.meals) {
      for (const dish of meal.dishes) {
        const at = dish.ingredients.findIndex((candidate) => candidate.id === updated.id)
        if (at !== -1) {
          dish.ingredients[at] = updated
          // `diet` is a shallowRef — the week arrives whole and is replaced
          // whole, so nothing under it is tracked. One ingredient changing
          // beneath it is the exception, and this is it saying so.
          triggerRef(diet)
          return true
        }
      }
    }
  }
  return false
}

/** A stored ingredient by its row id, with the cell it sits in. */
function findIngredient(id: number): IngredientAt | null {
  for (const row of mealRows.value) {
    for (const column of days.value) {
      const dish = storedDish(column.day, row.mealType, row.dishIndex)
      const found = dish?.ingredients.find((ingredient) => ingredient.id === id)
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
  if (!edit || edit.text.trim() === '') {
    return
  }
  const text = edit.text
  try {
    const dish = await dietsApi.parse({ text, slotName, dietId: diet.value?.id })
    // The text may have moved on while the request was in flight.
    if (edits[key]?.text === text) {
      edits[key] = { text, dish, parsing: false, failed: false }
    }
  } catch {
    if (edits[key]?.text === text) {
      edits[key] = { text, dish: edit.dish, parsing: false, failed: true }
    }
  }
}

/**
 * Holds the typed text and asks the backend to read it once the typing pauses.
 * Text that matches what is stored is not an edit at all, so the cell goes back
 * to being clean rather than standing marked for no difference.
 */
function setText(row: MealRow, day: DayOfWeek, text: string): void {
  const key = cellKey(row, day)
  const stored = renderDish(storedDish(day, row.mealType, row.dishIndex), row.label)

  clearTimer(key)
  if (text.trim() === stored.trim()) {
    delete edits[key]
    return
  }
  if (text.trim() === '') {
    // An emptied cell is a dish removed; there is nothing left to read.
    edits[key] = { text, dish: null, parsing: false, failed: false }
    return
  }
  edits[key] = { text, dish: edits[key]?.dish ?? null, parsing: true, failed: false }
  parseTimers.set(
    key,
    setTimeout(() => {
      void reparse(key, row.label)
    }, PARSE_DEBOUNCE_MS),
  )
}

/**
 * Appends a composed food to a cell. The text is the one the backend wrote for
 * it, so the sentence stays the only source of truth and is read back by the
 * same parser as anything typed.
 */
function appendFragment(row: MealRow, day: DayOfWeek, fragment: string): void {
  const current = textFor(row, day).trim()
  let next: string
  if (current === '') {
    next = fragment
  } else if (current.endsWith(':') || current.endsWith('+')) {
    next = `${current} ${fragment}`
  } else {
    next = `${current} + ${fragment}`
  }
  setText(row, day, next)
}

/** Puts one cell back the way it is stored. */
function revert(row: MealRow, day: DayOfWeek): void {
  const key = cellKey(row, day)
  clearTimer(key)
  delete edits[key]
}

function discardAll(): void {
  for (const key of Object.keys(edits)) {
    clearTimer(key)
    delete edits[key]
  }
}

/** Reads any cell still waiting on its debounce, so nothing is published stale. */
async function settleParses(): Promise<void> {
  const pending: Promise<void>[] = []
  for (const row of mealRows.value) {
    for (const column of days.value) {
      const key = cellKey(row, column.day)
      const edit = edits[key]
      if (edit && edit.text.trim() !== '' && (edit.parsing || edit.dish === null)) {
        clearTimer(key)
        pending.push(reparse(key, row.label))
      }
    }
  }
  await Promise.all(pending)
}

/**
 * A meal's dishes in row order. An emptied cell drops its dish, and a cell
 * written where the day had none is appended — the rows below do not shift,
 * because a day that never filled the last row simply has fewer dishes.
 */
function dishesRequest(day: DayOfWeek, mealType: MealType): RequestDish[] {
  const dishes: RequestDish[] = []
  for (const row of mealRows.value.filter((candidate) => candidate.mealType === mealType)) {
    if (edits[cellKey(row, day)]?.text.trim() === '') {
      continue
    }
    const dish = dishFor(day, mealType, row.dishIndex)
    if (!dish || dish.ingredients.length === 0) {
      continue
    }
    dishes.push({
      name: dish.name,
      // Whatever the backend last held for this cell: the text just typed, for
      // an edited one, since the parse answers with the sentence it read. Never
      // a rebuilt line — a cell with none stays without one rather than gaining
      // an invented original.
      rawText: dish.rawText,
      ingredients: dish.ingredients.map((ingredient) => ({
        name: ingredient.name,
        quantity: ingredient.quantity,
        unit: ingredient.unit,
        foodItemId: ingredient.foodItemId,
        bedcaFoodId: ingredient.bedcaFoodId,
        state: ingredient.state,
        size: ingredient.size,
        foodMeasureId: ingredient.foodMeasureId,
      })),
    })
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
    totalsFor,
    isEdited,
    // writes
    load,
    refresh,
    select,
    setText,
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
