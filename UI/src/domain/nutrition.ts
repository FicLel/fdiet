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
  /** Matched, but in no total: a unit nothing can weigh (`1 cdta`), or a state mismatch. */
  unmeasured: number
  /**
   * Of `unmeasured`, weighed on one side of cooking and matched to a food on the
   * other — `55 g en seco` against `Lenteja, cocida`. Never totalled, yield or not.
   */
  unmeasuredByState: number
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
  unmeasuredByState: 0,
}

/** A running total that stays null until a known figure arrives; an unknown adds nothing. */
export function addKnown(total: number | null, value: number | null | undefined): number | null {
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
    if (ingredient.foodItemId === null && ingredient.compositionFoodId === null) {
      totals.unmatched++
      continue
    }
    if (!measured(ingredient.nutrition)) {
      totals.unmeasured++
      if (ingredient.stateMismatch) {
        totals.unmeasuredByState++
      }
      continue
    }
    totals.counted++
    totals.kcal = addKnown(totals.kcal, scaled(ingredient.nutrition?.energyKcal))
    totals.proteinG = addKnown(totals.proteinG, scaled(ingredient.nutrition?.proteinG))
    totals.carbohydratesG = addKnown(totals.carbohydratesG, scaled(ingredient.nutrition?.carbohydratesG))
    totals.fatG = addKnown(totals.fatG, scaled(ingredient.nutrition?.fatG))
  }
  return totals
}

/** Whether every ingredient of a dish landed in the total. */
export function complete(totals: DishTotals): boolean {
  return totals.ingredients > 0 && totals.counted === totals.ingredients
}

/** Several cells or days as one: figures added where known, every count summed. */
export function sumTotals(parts: DishTotals[]): DishTotals {
  return parts.reduce<DishTotals>(
    (total, part) => ({
      kcal: addKnown(total.kcal, part.kcal),
      proteinG: addKnown(total.proteinG, part.proteinG),
      carbohydratesG: addKnown(total.carbohydratesG, part.carbohydratesG),
      fatG: addKnown(total.fatG, part.fatG),
      ingredients: total.ingredients + part.ingredients,
      counted: total.counted + part.counted,
      unmatched: total.unmatched + part.unmatched,
      unmeasured: total.unmeasured + part.unmeasured,
      unmeasuredByState: total.unmeasuredByState + part.unmeasuredByState,
    }),
    EMPTY_TOTALS,
  )
}

/**
 * Why the ingredients left out of a total were left out: `2 sin vincular`,
 * `1 sin peso`, `1 crudo/cocinado no coincide`. Empty when nothing was.
 */
export function uncountedReasons(totals: DishTotals): string[] {
  const unweighed = totals.unmeasured - totals.unmeasuredByState
  const parts: string[] = []
  if (totals.unmatched > 0) {
    parts.push(`${totals.unmatched} sin vincular`)
  }
  if (unweighed > 0) {
    parts.push(`${unweighed} sin peso`)
  }
  if (totals.unmeasuredByState > 0) {
    parts.push(`${totals.unmeasuredByState} crudo/cocinado no coincide`)
  }
  return parts
}
