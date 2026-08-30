import type { Dish, Nutrition } from '@/api/types'

/**
 * A dish carries no totals of its own — the backend summarises a meal, a day
 * and the week, and hands each ingredient its own scaled figures. Adding the
 * ingredients up here costs nothing and keeps the counts with the total.
 *
 * **A total always travels with its counts.** A dish whose ingredients are half
 * unmatched adds up to a number that looks exactly like a complete one.
 */
export interface DishTotals {
  kcal: number | null
  proteinG: number | null
  carbohydratesG: number | null
  fatG: number | null
  ingredients: number
  /** Matched, measurable, and in the total. */
  counted: number
  /** Not yet matched to a food: nothing is known about it. */
  unmatched: number
  /** Matched, but written in a unit nothing can weigh — `1 cdta`. */
  unmeasured: number
}

export const EMPTY_TOTALS: DishTotals = {
  kcal: null,
  proteinG: null,
  carbohydratesG: null,
  fatG: null,
  ingredients: 0,
  counted: 0,
  unmatched: 0,
  unmeasured: 0,
}

function add(total: number | null, value: number | null | undefined): number | null {
  if (value === null || value === undefined) {
    return total
  }
  return (total ?? 0) + value
}

function measured(nutrition: Nutrition | null): boolean {
  return nutrition !== null && Object.values(nutrition).some((value) => value !== null)
}

export function dishTotals(dish: Dish | undefined): DishTotals {
  if (!dish) {
    return EMPTY_TOTALS
  }
  const totals: DishTotals = { ...EMPTY_TOTALS, ingredients: dish.ingredients.length }
  for (const ingredient of dish.ingredients) {
    if (ingredient.foodItemId === null && ingredient.bedcaFoodId === null) {
      totals.unmatched++
      continue
    }
    if (!measured(ingredient.nutrition)) {
      totals.unmeasured++
      continue
    }
    totals.counted++
    totals.kcal = add(totals.kcal, ingredient.nutrition?.energyKcal)
    totals.proteinG = add(totals.proteinG, ingredient.nutrition?.proteinG)
    totals.carbohydratesG = add(totals.carbohydratesG, ingredient.nutrition?.carbohydratesG)
    totals.fatG = add(totals.fatG, ingredient.nutrition?.fatG)
  }
  return totals
}

/** Whether every ingredient of a dish landed in the total. */
export function complete(totals: DishTotals): boolean {
  return totals.ingredients > 0 && totals.counted === totals.ingredients
}
