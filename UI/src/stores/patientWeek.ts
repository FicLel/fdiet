import { computed, ref, shallowRef } from 'vue'
import { ApiError } from '@/api/http'
import { dietsApi } from '@/api/diets'
import { journalApi, type LogExtraFoodRequest } from '@/api/journal'
import type {
  DayOfWeek,
  Diet,
  DietJournal,
  ExtraFood,
  MealType,
  Nutrition,
} from '@/api/types'
import { buildRows, dishAt, type GridRow, type MealRow } from '@/domain/slots'
import { renderDish } from '@/domain/dishText'
import { dishTotals, type DishTotals } from '@/domain/nutrition'
import { addDays, dayName, dayNumber, longDate, mondayOf, shortDayName, WEEK } from '@/domain/week'

/**
 * The week as the patient reads it, and the journal they write beside it.
 *
 * **The plan is read-only here.** Nothing on this screen changes a dish: the
 * nutritionist writes the week and publishes it, and the patient sees whatever
 * was last published. The two things a patient may do — say what they thought
 * of a plate, and record something they ate that was not on the plan — are
 * their own rows in `dish_scores` and `extra_foods`, and neither touches the
 * diet.
 *
 * A day therefore has two totals that must not be added up as one. `plan` is
 * what the week prescribes; `extra` is what was eaten beside it. The design
 * draws them as two segments of one bar for exactly that reason — a day 300
 * kcal over because the plan is heavy and a day 300 kcal over because of an
 * ice cream are different days.
 */

/**
 * Placeholders, browser-held, until the backend carries a goal and a patient.
 * The same two the builder holds: there is no `users` context and no goal
 * field, so these are the only numbers on the screen nothing stands behind.
 */
const PLACEHOLDER_TARGET_KCAL = 1900
const PLACEHOLDER_PROTEIN_G = 120
const PLACEHOLDER_GOAL = '1.900 kcal/día · 120 g de proteína o más'
const PLACEHOLDER_GOAL_NOTE = 'Reparto en 7 tomas; no saltarse la media mañana ni la merienda'

/** More than this off the goal is worth colouring; less is rounding. */
export const TOLERANCE_KCAL = 60

export type PatientView = 'week' | 'day'

/** One day as the patient's screens read it: the plan, the extras, and the sum. */
export interface PatientDay {
  day: DayOfWeek
  name: string
  /** `Lun` — the tablet header and the mobile strip have no room for more. */
  abbr: string
  date: string
  longDate: string
  /** What the week prescribes for the day. */
  plan: DishTotals
  /** What was eaten beside it. */
  extras: ExtraFood[]
  extraKcal: number | null
  /** Plan plus extras — what the day actually came to. */
  kcal: number | null
  proteinG: number | null
  carbohydratesG: number | null
  fatG: number | null
  /** The day the week is being read on, when the week covers it at all. */
  isToday: boolean
}

/** `2026-08-30`, the way the backend writes a date, for a same-day comparison. */
function isoOf(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

const diet = shallowRef<Diet | null>(null)
const journal = shallowRef<DietJournal | null>(null)
const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const error = ref<string | null>(null)

const view = ref<PatientView>('week')
const selectedDay = ref<DayOfWeek | null>(null)

/** Which cell's card is open on a touch screen, where there is no hover. */
const openCell = ref<string | null>(null)

/** Set while a star or an extra is in flight, so the screen never lies. */
const saving = ref(false)

const rows = computed<GridRow[]>(() => (diet.value ? buildRows(diet.value) : []))

const mealRows = computed<MealRow[]>(() =>
  rows.value.filter((row): row is MealRow => row.kind === 'meal'),
)

const targetKcal = computed(() => PLACEHOLDER_TARGET_KCAL)
const targetProteinG = computed(() => PLACEHOLDER_PROTEIN_G)
const goal = computed(() => PLACEHOLDER_GOAL)
const goalNote = computed(() => PLACEHOLDER_GOAL_NOTE)

function storedDish(day: DayOfWeek, mealType: MealType, dishIndex: number) {
  return dishAt(
    diet.value?.days.find((candidate) => candidate.day === day),
    mealType,
    dishIndex,
  )
}

/** What a cell reads: the sentence the nutritionist wrote, wherever it is kept. */
function textFor(row: MealRow, day: DayOfWeek): string {
  return renderDish(storedDish(day, row.mealType, row.dishIndex), row.label)
}

function totalsFor(row: MealRow, day: DayOfWeek): DishTotals {
  return dishTotals(storedDish(day, row.mealType, row.dishIndex))
}

function plus(total: number | null, value: number | null | undefined): number | null {
  return value === null || value === undefined ? total : (total ?? 0) + value
}

/** The plan's total for a day, added up from the cells the grid draws. */
function planTotals(day: DayOfWeek): DishTotals {
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

function extrasOf(day: DayOfWeek): ExtraFood[] {
  return journal.value?.days.find((candidate) => candidate.day === day)?.extras ?? []
}

function sumOf(entries: ExtraFood[], read: (n: Nutrition) => number | null): number | null {
  let total: number | null = null
  for (const entry of entries) {
    if (entry.nutrition) {
      total = plus(total, read(entry.nutrition))
    }
  }
  return total
}

/** The days the diet actually has, Monday first, dated off `startedOn`. */
const days = computed<PatientDay[]>(() => {
  const plan = diet.value
  if (!plan) {
    return []
  }
  const first = mondayOf(plan.startedOn)
  const present = new Set(plan.days.map((day) => day.day))
  const now = isoOf(new Date())
  return WEEK.filter((day) => present.has(day)).map((day) => {
    const date = addDays(first, WEEK.indexOf(day))
    const planned = planTotals(day)
    const extras = extrasOf(day)
    const extraKcal = sumOf(extras, (n) => n.energyKcal)
    return {
      day,
      name: dayName(day),
      abbr: shortDayName(day),
      date: dayNumber(date),
      longDate: longDate(date),
      plan: planned,
      extras,
      extraKcal,
      kcal: plus(planned.kcal, extraKcal),
      proteinG: plus(planned.proteinG, sumOf(extras, (n) => n.proteinG)),
      carbohydratesG: plus(planned.carbohydratesG, sumOf(extras, (n) => n.carbohydratesG)),
      fatG: plus(planned.fatG, sumOf(extras, (n) => n.fatG)),
      isToday: isoOf(date) === now,
    }
  })
})

/** The day the week is being read on, or the first one when it covers none. */
const todayOrFirst = computed<PatientDay | null>(
  () => days.value.find((day) => day.isToday) ?? days.value[0] ?? null,
)

const monday = computed(() => (diet.value ? mondayOf(diet.value.startedOn) : new Date()))

const weekAverageKcal = computed(() => {
  const columns = days.value
  if (columns.length === 0) {
    return null
  }
  return columns.reduce((sum, column) => sum + (column.kcal ?? 0), 0) / columns.length
})

/** Every ingredient of the week, so an average is read against its coverage. */
const weekCoverage = computed<DishTotals>(() =>
  days.value.reduce<DishTotals>(
    (total, day) => ({
      kcal: null,
      proteinG: null,
      carbohydratesG: null,
      fatG: null,
      ingredients: total.ingredients + day.plan.ingredients,
      counted: total.counted + day.plan.counted,
      unmatched: total.unmatched + day.plan.unmatched,
      unmeasured: total.unmeasured + day.plan.unmeasured,
    }),
    {
      kcal: null,
      proteinG: null,
      carbohydratesG: null,
      fatG: null,
      ingredients: 0,
      counted: 0,
      unmatched: 0,
      unmeasured: 0,
    },
  ),
)

const today = computed<PatientDay | null>(
  () => days.value.find((column) => column.day === selectedDay.value) ?? days.value[0] ?? null,
)

/**
 * The scores by slot, so a cell reads its own without walking the list. The key
 * is the slot the backend keys on, not a dish id.
 */
const scoreBySlot = computed<Record<string, number>>(() => {
  const map: Record<string, number> = {}
  for (const score of journal.value?.scores ?? []) {
    map[`${score.day}|${score.mealType}|${score.dishIndex}`] = score.score
  }
  return map
})

export function slotKey(row: MealRow, day: DayOfWeek): string {
  return `${day}|${row.mealType}|${row.dishIndex}`
}

function scoreFor(row: MealRow, day: DayOfWeek): number {
  return scoreBySlot.value[slotKey(row, day)] ?? 0
}

async function loadJournal(dietId: number): Promise<void> {
  journal.value = await journalApi.find(dietId)
}

async function load(): Promise<void> {
  status.value = 'loading'
  error.value = null
  try {
    const active = await dietsApi.active()
    diet.value = active
    await loadJournal(active.id)
    selectedDay.value = todayOrFirst.value?.day ?? null
    status.value = 'ready'
  } catch (cause) {
    diet.value = null
    journal.value = null
    error.value =
      cause instanceof ApiError && cause.status === 404
        ? 'Todavía no hay ninguna dieta activa. Tu nutricionista aún no ha publicado la semana.'
        : `No se pudo cargar la dieta${cause instanceof Error ? `: ${cause.message}` : ''}`
    status.value = 'error'
  }
}

function show(next: PatientView): void {
  view.value = next
}

function selectDay(day: DayOfWeek): void {
  selectedDay.value = day
}

function toggleCell(key: string): void {
  openCell.value = openCell.value === key ? null : key
}

/**
 * Scores a plate, or takes the score back when the same star is pressed again.
 *
 * The journal is refetched rather than patched in place: the average and its
 * count are the backend's arithmetic, and recomputing them here would be a
 * second implementation of the same sum, free to disagree with the first.
 */
async function score(row: MealRow, day: DayOfWeek, value: number): Promise<void> {
  const plan = diet.value
  if (!plan || saving.value) {
    return
  }
  saving.value = true
  error.value = null
  try {
    if (scoreFor(row, day) === value) {
      await journalApi.clearScore(plan.id, day, row.mealType, row.dishIndex)
    } else {
      await journalApi.score(plan.id, day, row.mealType, row.dishIndex, value)
    }
    await loadJournal(plan.id)
  } catch (cause) {
    error.value = `No se pudo guardar la puntuación${
      cause instanceof Error ? `: ${cause.message}` : ''
    }`
  } finally {
    saving.value = false
  }
}

async function logExtra(entry: LogExtraFoodRequest): Promise<boolean> {
  const plan = diet.value
  if (!plan || saving.value) {
    return false
  }
  saving.value = true
  error.value = null
  try {
    await journalApi.logExtra(plan.id, entry)
    await loadJournal(plan.id)
    return true
  } catch (cause) {
    error.value = `No se pudo añadir la comida${
      cause instanceof Error ? `: ${cause.message}` : ''
    }`
    return false
  } finally {
    saving.value = false
  }
}

async function removeExtra(extraId: number): Promise<void> {
  const plan = diet.value
  if (!plan || saving.value) {
    return
  }
  saving.value = true
  error.value = null
  try {
    await journalApi.removeExtra(plan.id, extraId)
    await loadJournal(plan.id)
  } catch (cause) {
    error.value = `No se pudo quitar la comida${
      cause instanceof Error ? `: ${cause.message}` : ''
    }`
  } finally {
    saving.value = false
  }
}

export function usePatientWeek() {
  return {
    // state
    diet,
    journal,
    status,
    error,
    view,
    selectedDay,
    openCell,
    saving,
    // placeholders
    targetKcal,
    targetProteinG,
    goal,
    goalNote,
    // derived
    rows,
    mealRows,
    days,
    today,
    monday,
    weekAverageKcal,
    weekCoverage,
    todayOrFirst,
    // reads
    textFor,
    totalsFor,
    scoreFor,
    // writes
    load,
    show,
    selectDay,
    toggleCell,
    score,
    logExtra,
    removeExtra,
  }
}
