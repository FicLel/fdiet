import type { DayOfWeek, DishIngredient } from '@/api/types'
import type { MealRow } from './slots'

/**
 * One stored ingredient and the cell it sits in — what the link drawer needs to
 * address it and to show where in the week it is.
 *
 * The id is the stored row's, so only a *stored* ingredient can be one of
 * these. An ingredient read out of a cell being edited has no row of its own
 * yet, and nothing for a PATCH to address; it is matched by publishing first.
 */
export type IngredientAt = Pick<
  DishIngredient,
  | 'name'
  | 'quantity'
  | 'quantityMax'
  | 'unit'
  | 'unitWording'
  | 'matchedName'
  | 'matchedSource'
  | 'compositionFoodId'
  | 'foodItemId'
  | 'state'
  | 'size'
  | 'measure'
  | 'measurePicked'
  | 'stateMismatch'
  | 'yieldHint'
> & {
  /** The stored row's id — what `PATCH …/ingredients/{id}` addresses. */
  id: number
  /** Matched, but nothing weighs the unit it is written in. */
  unweighed: boolean
  row: MealRow
  day: DayOfWeek
}

/**
 * Matched to a food, and still in no total: nothing weighs the unit it is written in.
 * A state mismatch is left out of the total for another reason, and says so itself.
 */
export function isUnweighed(ingredient: DishIngredient): boolean {
  return (
    (ingredient.compositionFoodId !== null || ingredient.foodItemId !== null) &&
    !ingredient.stateMismatch &&
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
        ...ingredient,
        id: ingredient.id,
        unweighed: isUnweighed(ingredient),
        row,
        day,
      }
}
