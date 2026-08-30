import type { Dish, DishIngredient } from '@/api/types'

/**
 * The nutritionist writes a cell as one line of text and the backend stores it
 * split — a dish name, then an ingredient per `+`, each with its quantity. This
 * puts the line back together so the grid shows what was written.
 *
 * `MealTextParser` gives an ingredient it could read no quantity for the default
 * `1 unidad`, holding the whole fragment as the name; rendering that back as
 * `(1 unidad)` would add a portion the diet never stated, so it is left off.
 */

const DEFAULT_UNIT = 'unidad'

export function renderIngredient(ingredient: DishIngredient): string {
  if (ingredient.quantity === 1 && ingredient.unit === DEFAULT_UNIT) {
    return ingredient.name
  }
  return `${ingredient.name} (${formatAmount(ingredient.quantity)} ${ingredient.unit})`
}

/**
 * @param rowLabel the label of the grid row. The parser falls back to it when
 *   the cell carried no `name:` of its own, and a label repeated back into the
 *   cell is noise rather than content.
 */
export function renderDish(dish: Dish | undefined, rowLabel: string): string {
  if (!dish) {
    return ''
  }
  const body = dish.ingredients.map(renderIngredient).join(' + ')
  const named = dish.name && dish.name !== rowLabel && dish.name !== body
  return named ? `${dish.name}: ${body}` : body
}

/** `0.5` reads as `0,5`; a whole number keeps no decimals at all. */
function formatAmount(amount: number): string {
  return Number.isInteger(amount) ? String(amount) : String(amount).replace('.', ',')
}
