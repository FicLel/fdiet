import type { RequestIngredient, RequestRecipe } from '@/api/diets'
import type { DishIngredient } from '@/api/types'

/**
 * An ingredient as it goes back to the backend — on a publish, a library save
 * or a plate's own recipe — field by field, so what the backend fills on the
 * way out (figures, names, suggestions) is not sent back in.
 *
 * `measurePicked` goes back as it came (FD-054): `false` hands the measure back
 * to the rule, which chooses again and so follows a criterion written since;
 * `true` keeps a measure a person picked.
 */
export function requestIngredient(ingredient: DishIngredient): RequestIngredient {
  return {
    name: ingredient.name,
    quantity: ingredient.quantity,
    quantityMax: ingredient.quantityMax,
    unit: ingredient.unit,
    foodItemId: ingredient.foodItemId,
    compositionFoodId: ingredient.compositionFoodId,
    state: ingredient.state,
    size: ingredient.size,
    foodMeasureId: ingredient.foodMeasureId,
    measurePicked: ingredient.measurePicked,
  }
}

/**
 * A plate's own recipe as a publish writes it: the text as typed and the
 * ingredients read out of it. Null when there is nothing to write — no
 * ingredients and no steps — and no ingredients when the text is blank.
 */
export function recipeRequest(
  text: string,
  steps: string,
  ingredients: readonly DishIngredient[],
): RequestRecipe | null {
  if (text.trim() === '' && steps.trim() === '') {
    return null
  }
  return {
    rawText: text.trim() || null,
    steps: steps.trim() || null,
    ingredients: (text.trim() === '' ? [] : ingredients).map(requestIngredient),
  }
}
