import type { DishIngredient, Recipe, UnitWording } from '@/api/types'
import { quantity } from './format'

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

/**
 * Recipe text with one more fragment at the end, joined the way a diet joins
 * ingredients: a `+` between them, none after a `:` or a `+` already typed.
 */
export function joinFragment(text: string, fragment: string): string {
  const current = text.trim()
  if (current === '') {
    return fragment
  }
  if (current.endsWith(':') || current.endsWith('+')) {
    return `${current} ${fragment}`
  }
  return `${current} + ${fragment}`
}

/** `40`, or `40-60` for a range nobody has settled — the way the parser reads it back. */
export function quantityText(ingredient: Pick<DishIngredient, 'quantity' | 'quantityMax'>): string {
  return ingredient.quantityMax === null
    ? formatAmount(ingredient.quantity)
    : `${formatAmount(ingredient.quantity)}-${formatAmount(ingredient.quantityMax)}`
}

/**
 * The unit for an amount, in the backend's wording: `unidad mediana` for
 * exactly one, `unidades medianas` for anything else — a range included. The
 * size and the gender it agrees with are the backend's; nothing here guesses
 * an ending. Without a wording (an older backend), the unit as stored.
 */
export function unitFor(
  amount: Pick<DishIngredient, 'quantity' | 'quantityMax'>,
  unit: string,
  wording: UnitWording | null | undefined,
): string {
  const one = amount.quantityMax === null && amount.quantity === 1
  return (one ? wording?.singular : wording?.plural) ?? unit
}

/**
 * `2 unidades medianas`, `1.200 g`, or `40-60 g` for a range nobody has settled
 * (which counts nowhere until a value in it is chosen): an ingredient's amount
 * on the nutritionist's lists, worded for reading.
 */
export function amountText(
  ingredient: Pick<DishIngredient, 'quantity' | 'quantityMax' | 'unit' | 'unitWording'>,
): string {
  const unit = unitFor(ingredient, ingredient.unit, ingredient.unitWording)
  return ingredient.quantityMax === null
    ? quantity(ingredient.quantity, unit)
    : `${quantityText(ingredient)} ${unit}`
}

/** Whether the parser gave the ingredient no quantity of its own (`sal`): `1 unidad` and no size. */
export function isUnstatedQuantity(ingredient: DishIngredient): boolean {
  return (
    ingredient.quantity === 1 &&
    ingredient.quantityMax === null &&
    ingredient.unit === DEFAULT_UNIT &&
    ingredient.size === null
  )
}

/**
 * One ingredient at a plate's servings, for the patient: `2 unidades medianas`
 * served 1,5 times is `3 unidades medianas`, served 0,5 times `1 unidad
 * mediana`. A range scales at both ends. Units only — never the grams a measure
 * weighs, which stay on the nutritionist's side.
 */
export function servedQuantity(ingredient: DishIngredient, servings: number): string {
  const scaled = {
    quantity: round(ingredient.quantity * servings),
    quantityMax: ingredient.quantityMax === null ? null : round(ingredient.quantityMax * servings),
  }
  return `${quantityText(scaled)} ${unitFor(scaled, ingredient.unit, ingredient.unitWording)}`
}

function round(value: number): number {
  return Math.round(value * 100) / 100
}

/** `0.5` reads as `0,5`; a whole number keeps no decimals at all. */
function formatAmount(amount: number): string {
  return Number.isInteger(amount) ? String(amount) : String(amount).replace('.', ',')
}
