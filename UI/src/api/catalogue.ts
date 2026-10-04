import { http, query } from './http'
import type { CompositionFood } from './compositionTypes'
import type { FoodItem, Page } from './types'

/**
 * The two halves of the food catalogue, searched by name.
 *
 * They answer different questions and the fix-up drawer keeps them apart:
 * `composition` holds the generic foods a diet is normally written in, from
 * CIQUAL 2025 and BLS 4.0 (`Lechuga`, `Pollo, pechuga, plancha` through
 * fdiet's Spanish crosswalk), `branded` the ~100k commercial products, keyed on
 * EAN, for the days a diet names a product outright.
 *
 * The `composition` search answers foods whose Spanish name or aliases share
 * the words first, then any food whose Spanish, English or original name
 * contains the text as typed, so a food the crosswalk does not name yet is
 * still found by `lettuce` or `laitue`. Callers show that order as it comes.
 * The `branded` search is `LIKE '%…%'` on the name under a case- and
 * accent-insensitive collation, so `lechuga` finds `Lechuga`.
 */
export const catalogueApi = {
  composition: (name: string, page = 0, size = 20) =>
    http.get<Page<CompositionFood>>(`/composition${query({ name, page, size })}`),

  branded: (name: string, page = 0, size = 20) =>
    http.get<Page<FoodItem>>(`/food${query({ name, page, size })}`),
}
