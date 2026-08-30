import type { Dish, DishIngredient } from '@/api/types'

/**
 * What a cell reads as.
 *
 * A diet stores the sentence it was written as (`rawText`) beside what was read
 * out of it, so almost always the answer is simply the sentence. `rebuild` is
 * the fallback for a dish stored before the column existed, or assembled by a
 * caller that never had one.
 *
 * **A rebuilt line is not the line that was written.** The backend keeps one
 * quantity per ingredient and no brackets in the name, so
 * `Tostada (60 gr) con tomate (80 gr)` rebuilds as
 * `Tostada con tomate (80 gr) (60 gr)` — the same food, a moved weight. It is
 * good enough to read and not good enough to store, which is why nothing ever
 * sends one back.
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
  return dish.rawText ?? rebuild(dish, rowLabel)
}

/** The line put back together from the parts, when the original is not kept. */
function rebuild(dish: Dish, rowLabel: string): string {
  const body = dish.ingredients.map(renderIngredient).join(' + ')
  const named = dish.name && dish.name !== rowLabel && dish.name !== body
  return named ? `${dish.name}: ${body}` : body
}

/** `0.5` reads as `0,5`; a whole number keeps no decimals at all. */
function formatAmount(amount: number): string {
  return Number.isInteger(amount) ? String(amount) : String(amount).replace('.', ',')
}
