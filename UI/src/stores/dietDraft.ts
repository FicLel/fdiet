import { computed, reactive, ref, shallowRef } from 'vue'
import { ApiError } from '@/api/http'
import { dietsApi, type DietRequest, type RequestDish, type RequestMeal } from '@/api/diets'
import type { DayOfWeek, Diet, Dish, MealType } from '@/api/types'
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
 * The kcal a cell shows are worked out here rather than taken from the diet's
 * own summaries, because a figure has to move as the text is typed — and it is
 * the same arithmetic either way, over the same per-ingredient figures.
 */

/** How long a pause in the typing means the cell is ready to be read. */
const PARSE_DEBOUNCE_MS = 450

/**
 * Placeholder. The backend has no goal and no patient — the `users` bounded
 * context was removed — so the goal line and the patient selector are the
 * nutritionist's own note for now, held in the browser and never sent.
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
const publishing = ref(false)
const error = ref<string | null>(null)
const edits = reactive<Record<string, CellEdit>>({})
const selection = ref<{ day: DayOfWeek; mealType: MealType; dishIndex: number } | null>(null)

/** Placeholders, browser-only, until the backend carries a goal and a patient. */
const goalNote = ref('1.900 kcal/día · 120 g de proteína o más')
const targetKcal = ref(PLACEHOLDER_TARGET_KCAL)

const parseTimers = new Map<string, ReturnType<typeof setTimeout>>()

const rows = computed<GridRow[]>(() => (diet.value ? buildRows(diet.value) : []))

const mealRows = computed<MealRow[]>(() =>
  rows.value.filter((row): row is MealRow => row.kind === 'meal'),
)

/** The days the diet actually has, Monday first, dated off `startedOn`. */
const days = computed<DayColumn[]>(() => {
  const plan = diet.value
  if (!plan) {
    return []
  }
  const first = mondayOf(plan.startedOn)
  const present = new Set(plan.days.map((day) => day.day))
  return WEEK.filter((day) => present.has(day)).map((day) => {
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
  const columns = days.value
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

function selectFirstCell(): void {
  const row = mealRows.value[0]
  const day = days.value[0]
  selection.value =
    row && day ? { day: day.day, mealType: row.mealType, dishIndex: row.dishIndex } : null
}

async function load(): Promise<void> {
  status.value = 'loading'
  error.value = null
  try {
    diet.value = await dietsApi.active()
    status.value = 'ready'
    selectFirstCell()
  } catch (cause) {
    diet.value = null
    error.value =
      cause instanceof ApiError && cause.status === 404
        ? 'No hay ninguna dieta activa. Importa una desde un libro de Excel para empezar.'
        : `No se pudo cargar la dieta${cause instanceof Error ? `: ${cause.message}` : ''}`
    status.value = 'error'
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
    const dish = await dietsApi.parse({ text, slotName })
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
    meals.push({ type: row.mealType, name: meal?.name ?? row.sub ?? row.label, dishes })
  }
  return meals
}

function weekRequest(plan: Diet): DietRequest {
  return {
    name: plan.name,
    startedOn: plan.startedOn,
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
    discardAll()
  } catch (cause) {
    error.value = `No se pudieron publicar los cambios${
      cause instanceof Error ? `: ${cause.message}` : ''
    }`
  } finally {
    publishing.value = false
  }
}

export function useDietDraft() {
  return {
    // state
    diet,
    status,
    error,
    publishing,
    edits,
    selection,
    goalNote,
    targetKcal,
    // derived
    rows,
    mealRows,
    days,
    monday,
    dirtyCount,
    weekAverageKcal,
    selectedRow,
    selectedDay,
    // reads
    dishFor,
    storedDish,
    textFor,
    totalsFor,
    isEdited,
    // writes
    load,
    select,
    setText,
    revert,
    discardAll,
    publish,
  }
}
