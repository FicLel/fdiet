/**
 * The figures of a food and the totals worked out from them, as the backend
 * sends them. Kept apart from `types.ts` so neither file outgrows the limit;
 * re-exported there so imports stay as they were.
 */

export interface Nutrition {
  energyKcal: number | null
  proteinG: number | null
  fatG: number | null
  saturatedFatG: number | null
  carbohydratesG: number | null
  sugarsG: number | null
  fiberG: number | null
  sodiumMg: number | null
}

/** A total never travels without the counts it was worked out over. */
export interface NutritionSummary {
  totals: Nutrition
  ingredients: number
  counted: number
  unmatched: number
  unmeasured: number
  /** Of `counted`, how many were weighed through a household measure rather than written in grams. */
  countedByMeasure: number
  /**
   * Of `unmeasured`, how many were weighed on one side of cooking and matched to
   * a food published on the other (`55 g en seco` against `Lenteja, cocida`).
   * Never totalled, even where a published yield exists.
   */
  unmeasuredByState: number
}

/** One published figure as it left the source: the number and its own unit. */
export interface Nutrient {
  value: number
  unit: string
}
