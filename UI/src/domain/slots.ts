import type { Diet, DietDay, Dish, Meal, MealType } from '@/api/types'

/**
 * The grid the nutritionist writes in has one row per *dish slot*, not one per
 * meal: `Comida` is a band across the week with `Primer plato`, `Segundo plato`
 * and `Postre` under it. The backend keeps the five meal slots and orders the
 * dishes inside each, so a row is a `(MealType, dish index)` pair.
 *
 * The rows are derived from the diet rather than fixed, because neither the
 * meals a diet uses nor the number of dishes in one is decided here — the
 * workbook the nutritionist wrote decides both.
 */

/** The order the meals run in the day, whatever order they arrive in. */
export const MEAL_ORDER: MealType[] = [
  'BREAKFAST',
  'MORNING_SNACK',
  'LUNCH',
  'AFTERNOON_SNACK',
  'DINNER',
]

/** How a diet names the dishes of a meal that has several. */
const DISH_LABELS = ['Primer plato', 'Segundo plato', 'Postre']

function dishLabel(index: number): string {
  return DISH_LABELS[index] ?? `Plato ${index + 1}`
}

/** A band naming a meal whose dishes are the rows under it. */
export interface BandRow {
  kind: 'band'
  key: string
  label: string
}

/** One editable row: the same dish slot across every day of the week. */
export interface MealRow {
  kind: 'meal'
  key: string
  /** `Desayuno`, or `Primer plato` under the `Comida` band. */
  label: string
  /** The band this row belongs to, blank for a meal of a single dish. */
  sub: string
  mealType: MealType
  dishIndex: number
  /** How the edit panel names the slot: `Comida · Primer plato`. */
  slotLabel: string
}

export type GridRow = BandRow | MealRow

/** What a meal is called when the diet has not named it yet. */
export const DEFAULT_MEAL_NAMES: Record<MealType, string> = {
  BREAKFAST: 'Desayuno',
  MORNING_SNACK: 'Media mañana',
  LUNCH: 'Comida',
  AFTERNOON_SNACK: 'Merienda',
  DINNER: 'Cena',
}

/**
 * The rows the editor offers whatever the diet already holds, so a new week has
 * somewhere to be written and a written one has room for one more dish. Only
 * the editor uses it: the patient reads the rows the week actually fills.
 */
const TEMPLATE_DISHES: Record<MealType, number> = {
  BREAKFAST: 1,
  MORNING_SNACK: 1,
  LUNCH: 3,
  AFTERNOON_SNACK: 1,
  DINNER: 2,
}

/** The label the diet gave a meal, or the usual one if it never named it. */
function mealName(diet: Diet, type: MealType): string {
  for (const day of diet.days) {
    const meal = day.meals.find((candidate) => candidate.type === type)
    if (meal?.name) {
      return meal.name
    }
  }
  return DEFAULT_MEAL_NAMES[type]
}

/** The most dishes any day puts in this meal — the number of rows it needs. */
function dishCount(diet: Diet, type: MealType): number {
  let most = 0
  for (const day of diet.days) {
    const meal = day.meals.find((candidate) => candidate.type === type)
    most = Math.max(most, meal?.dishes.length ?? 0)
  }
  return most
}

/**
 * @param options.template also offer the editor's usual slots — five meals, three
 *   dishes at lunch and two at dinner — where the diet has fewer.
 */
export function buildRows(diet: Diet, options: { template?: boolean } = {}): GridRow[] {
  const rows: GridRow[] = []
  for (const type of MEAL_ORDER) {
    const dishes = Math.max(dishCount(diet, type), options.template ? TEMPLATE_DISHES[type] : 0)
    if (dishes === 0) {
      continue
    }
    const name = mealName(diet, type)
    if (dishes === 1) {
      rows.push({
        kind: 'meal',
        key: `${type}-0`,
        label: name,
        sub: '',
        mealType: type,
        dishIndex: 0,
        slotLabel: name,
      })
      continue
    }
    rows.push({ kind: 'band', key: `band-${type}`, label: name })
    for (let index = 0; index < dishes; index++) {
      rows.push({
        kind: 'meal',
        key: `${type}-${index}`,
        label: dishLabel(index),
        sub: name,
        mealType: type,
        dishIndex: index,
        slotLabel: `${name} · ${dishLabel(index)}`,
      })
    }
  }
  return rows
}

export function mealOf(day: DietDay | undefined, type: MealType): Meal | undefined {
  return day?.meals.find((meal) => meal.type === type)
}

/** The dish in one cell, or undefined when that day left the slot empty. */
export function dishAt(
  day: DietDay | undefined,
  type: MealType,
  dishIndex: number,
): Dish | undefined {
  return mealOf(day, type)?.dishes[dishIndex]
}

/** Identifies a cell across the week: the row's slot and the day it sits in. */
export function cellKey(row: MealRow, day: string): string {
  return `${day}|${row.mealType}|${row.dishIndex}`
}
