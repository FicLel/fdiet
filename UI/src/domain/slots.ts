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

/** The label the diet gave a meal, or the enum name if it never named it. */
function mealName(diet: Diet, type: MealType): string {
  for (const day of diet.days) {
    const meal = day.meals.find((candidate) => candidate.type === type)
    if (meal?.name) {
      return meal.name
    }
  }
  return type
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

export function buildRows(diet: Diet): GridRow[] {
  const rows: GridRow[] = []
  for (const type of MEAL_ORDER) {
    const dishes = dishCount(diet, type)
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
