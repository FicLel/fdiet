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

/**
 * The attribution each table's licence (CC BY 4.0) requires wherever its
 * figures are shown, copied word for word from the backend's
 * `CompositionSource` (no endpoint hands both over without a sync), and the
 * page each one is credited to.
 */
export const SOURCE_ATTRIBUTIONS: readonly { source: CompositionSource; text: string; url: string }[] = [
  {
    source: 'CIQUAL',
    text:
      'ANSES. Ciqual French food composition table 2025. https://ciqual.anses.fr/ — ' +
      'doi:10.5281/zenodo.17550133. CC BY 4.0.',
    url: 'https://ciqual.anses.fr/',
  },
  {
    source: 'BLS',
    text:
      'Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0 — ' +
      'Deutsche Nährstoffdatenbank. Karlsruhe. DOI: 10.25826/Data20251217-134202-0. ' +
      'CC BY 4.0.',
    url: 'https://blsdb.de/',
  },
]

export function compositionSourceLabel(source: CompositionSource | null | undefined): string | null {
  return source ? SOURCE_LABELS[source] : null
}
