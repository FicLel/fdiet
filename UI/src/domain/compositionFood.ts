import type { CompositionFood } from '@/api/compositionTypes'

/**
 * How a CIQUAL / BLS food is named on screen: fdiet's Spanish name when the
 * crosswalk gives one, else the source's English name, else its own.
 */
export function compositionFoodName(food: CompositionFood): string {
  return food.nameEs ?? food.nameEn ?? food.nameOriginal
}

/**
 * The phase between re-keying and re-matching (FD-033 C → D): the criteria
 * name CIQUAL / BLS foods while every ingredient and extra is still matched to
 * BEDCA, so a criterion saved now weighs nothing yet. Said plainly, not shown
 * as an error.
 */
export const CRITERIA_PENDING_NOTE =
  'Tus criterios se guardan sobre un alimento de CIQUAL o BLS. Los ingredientes y extras siguen ' +
  'vinculados a BEDCA, así que todavía no pesan ninguno: se aplicarán cuando se vuelvan a vincular ' +
  'en la próxima fase.'
