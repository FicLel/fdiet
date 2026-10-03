import { http, query } from './http'
import type { CompositionFood } from './compositionTypes'
import type { BedcaFood, FoodItem, Page } from './types'

/**
 * The two halves of the food catalogue, searched by name.
 *
 * They answer different questions and the fix-up drawer keeps them apart:
 * `bedca` holds the 957 generic Spanish foods a diet is normally written in
 * (`Lechuga`, `Pollo, pechuga, plancha`), `branded` the ~100k commercial
 * products, keyed on EAN, for the days a diet names a product outright.
 *
 * The `bedca` search is word by word — accents, case and plurals ignored — and
 * answers in rank order, best first, so `pan de molde` finds
 * `Pan blanco, de molde, tostado`; a blank name is an alphabetical page and a
 * term nothing matches an empty one. Callers show that order as it comes.
 * The `branded` search is `LIKE '%…%'` on the name under a case- and
 * accent-insensitive collation, so `lechuga` finds `Lechuga`.
 *
 * `composition` searches CIQUAL 2025 and BLS 4.0: foods whose Spanish name or
 * aliases share the words first, then any food whose Spanish, English or
 * original name contains the text as typed, so a food the crosswalk does not
 * name yet is still found by `lettuce` or `laitue`.
 */
export const catalogueApi = {
  bedca: (name: string, page = 0, size = 20) =>
    http.get<Page<BedcaFood>>(`/bedca${query({ name, page, size })}`),

  composition: (name: string, page = 0, size = 20) =>
    http.get<Page<CompositionFood>>(`/composition${query({ name, page, size })}`),

  branded: (name: string, page = 0, size = 20) =>
    http.get<Page<FoodItem>>(`/food${query({ name, page, size })}`),
}
