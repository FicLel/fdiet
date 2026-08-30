import { http, query } from './http'
import type { BedcaFood, FoodItem, Page } from './types'

/**
 * The two halves of the food catalogue, searched by name.
 *
 * They answer different questions and the fix-up drawer keeps them apart:
 * `bedca` holds the 957 generic Spanish foods a diet is normally written in
 * (`Lechuga`, `Pollo, pechuga, plancha`), `branded` the ~100k commercial
 * products, keyed on EAN, for the days a diet names a product outright.
 *
 * Both searches are `LIKE '%…%'` on the name under a case- and
 * accent-insensitive collation, so `lechuga` finds `Lechuga`.
 */
export const catalogueApi = {
  bedca: (name: string, page = 0, size = 20) =>
    http.get<Page<BedcaFood>>(`/bedca${query({ name, page, size })}`),

  branded: (name: string, page = 0, size = 20) =>
    http.get<Page<FoodItem>>(`/food${query({ name, page, size })}`),
}
