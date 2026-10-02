import type { Dish, DishIngredient, Nutrition } from '@/api/types'

/**
 * Each ingredient carries its own figures for one serving of its recipe, and a
 * plate is that recipe served `servings` times. Adding them up here, rather than
 * taking the plate's own summary, lets a figure move while a recipe is being
 * typed — the same arithmetic either way.
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

/** A plate's ingredients: its recipe's, or none for a plate that is a description only. */
export function ingredientsOf(dish: Dish | undefined): DishIngredient[] {
  return dish?.recipe?.ingredients ?? []
}

export function dishTotals(dish: Dish | undefined): DishTotals {
  if (!dish) {
    return EMPTY_TOTALS
  }
  return recipeTotals(ingredientsOf(dish), dish.servings)
}

/** One serving's ingredients, `servings` times over. The counts are not scaled. */
export function recipeTotals(ingredients: DishIngredient[], servings = 1): DishTotals {
  const scaled = (value: number | null | undefined): number | null | undefined =>
    value === null || value === undefined ? value : value * servings
  const totals: DishTotals = { ...EMPTY_TOTALS, ingredients: ingredients.length }
  for (const ingredient of ingredients) {
    if (ingredient.foodItemId === null && ingredient.bedcaFoodId === null) {
      totals.unmatched++
      continue
    }
    if (!measured(ingredient.nutrition)) {
      totals.unmeasured++
      continue
    }
    totals.counted++
    totals.kcal = add(totals.kcal, scaled(ingredient.nutrition?.energyKcal))
    totals.proteinG = add(totals.proteinG, scaled(ingredient.nutrition?.proteinG))
    totals.carbohydratesG = add(totals.carbohydratesG, scaled(ingredient.nutrition?.carbohydratesG))
    totals.fatG = add(totals.fatG, scaled(ingredient.nutrition?.fatG))
  }
  return totals
}

/** Whether every ingredient of a dish landed in the total. */
export function complete(totals: DishTotals): boolean {
  return totals.ingredients > 0 && totals.counted === totals.ingredients
}
