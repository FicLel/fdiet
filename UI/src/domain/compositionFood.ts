import type { CompositionFood, CompositionSource } from '@/api/compositionTypes'

/**
 * How a CIQUAL / BLS food is named on screen: fdiet's Spanish name when the
 * crosswalk gives one, else the source's English name, else its own.
 */
export function compositionFoodName(food: CompositionFood): string {
  return food.nameEs ?? food.nameEn ?? food.nameOriginal
}

/**
 * The edition of each table, as the backend's `sourceLabel` writes it. Used
 * where only the `matchedSource` code travels — an ingredient or an extra
 * already matched — so a match still says which table its figures come from.
 */
const SOURCE_LABELS: Record<CompositionSource, string> = {
  CIQUAL: 'CIQUAL 2025',
  BLS: 'BLS 4.0',
}

export function compositionSourceLabel(source: CompositionSource | null | undefined): string | null {
  return source ? SOURCE_LABELS[source] : null
}
