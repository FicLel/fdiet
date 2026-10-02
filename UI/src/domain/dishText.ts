import type { DishIngredient, Recipe } from '@/api/types'

/**
 * What a recipe's ingredients read as.
 *
 * A recipe stores the text it was written as (`rawText`) beside what was read
 * out of it, so almost always the answer is simply that text. `rebuild` is the
 * fallback for a recipe stored before the column existed, or assembled by a
 * caller that never had one.
 *
 * **A rebuilt line is not the line that was written.** The backend keeps one
 * quantity per ingredient and no brackets in the name, so
 * `Tostada (60 gr) con tomate (80 gr)` rebuilds as
 * `Tostada con tomate (80 gr) + …` — the same food, a moved weight. It is good
 * enough to read and not good enough to store, which is why nothing ever sends
 * one back.
 *
 * `MealTextParser` gives an ingredient it could read no quantity for the default
 * `1 unidad`, holding the whole fragment as the name; rendering that back as
 * `(1 unidad)` would add a portion the diet never stated, so it is left off.
 */

const DEFAULT_UNIT = 'unidad'

export function renderIngredient(ingredient: DishIngredient): string {
  if (ingredient.quantity === 1 && ingredient.quantityMax === null && ingredient.unit === DEFAULT_UNIT) {
    return ingredient.name
  }
  return `${ingredient.name} (${quantityText(ingredient)} ${ingredient.unit})`
}

/** The recipe's ingredients as text: as written, or put back together when that is not kept. */
export function renderRecipe(recipe: Recipe | null | undefined): string {
  if (!recipe) {
    return ''
  }
  return recipe.rawText ?? recipe.ingredients.map(renderIngredient).join(' + ')
}

/** `40`, or `40-60` for a range nobody has settled — the way the parser reads it back. */
export function quantityText(ingredient: Pick<DishIngredient, 'quantity' | 'quantityMax'>): string {
  return ingredient.quantityMax === null
    ? formatAmount(ingredient.quantity)
    : `${formatAmount(ingredient.quantity)}-${formatAmount(ingredient.quantityMax)}`
}

/**
 * One ingredient at a plate's servings, for the patient: `2 huevos` served 1,5
 * times is `3 unidad`. A range scales at both ends.
 */
export function servedQuantity(ingredient: DishIngredient, servings: number): string {
  const scaled = {
    quantity: round(ingredient.quantity * servings),
    quantityMax: ingredient.quantityMax === null ? null : round(ingredient.quantityMax * servings),
  }
  return `${quantityText(scaled)} ${ingredient.unit}`
}

function round(value: number): number {
  return Math.round(value * 100) / 100
}

/** `0.5` reads as `0,5`; a whole number keeps no decimals at all. */
function formatAmount(amount: number): string {
  return Number.isInteger(amount) ? String(amount) : String(amount).replace('.', ',')
}
