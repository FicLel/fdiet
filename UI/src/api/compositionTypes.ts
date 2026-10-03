/* ---------------------------------------------------------------------------
 * The open composition tables — CIQUAL 2025 (ANSES) and BLS 4.0 (Max
 * Rubner-Institut), both CC BY 4.0, served from one table by
 * `/api/composition`.
 *
 * BEDCA's replacement in waiting: the nutritionist's measure criteria and the
 * published reference rows already name these foods; ingredients and extras
 * still point at BEDCA until they are re-matched. Kept apart from `types.ts`
 * so neither file outgrows the limit.
 * ------------------------------------------------------------------------- */

import type { Nutrient, Nutrition } from './types'

/** The two tables, as the backend names them. */
export type CompositionSource = 'CIQUAL' | 'BLS'

/**
 * One food of CIQUAL or BLS, with its source and the attribution its licence
 * asks for wherever its figures are shown.
 *
 * `nameEs`, `aliases`, `namePreferred`, `nameReviewed` and `ediblePortion` are
 * fdiet's own Spanish crosswalk, not the source's: null or empty on a food it
 * does not name yet. `energyPublished` false is a food whose energy the source
 * leaves blank — never read as zero.
 */
export interface CompositionFood {
  id: number
  source: CompositionSource
  sourceLabel: string
  attribution: string
  sourceCode: string
  nameOriginal: string
  nameEn: string | null
  foodGroupCode: string | null
  nameEs: string | null
  aliases: string[]
  namePreferred: boolean
  nameReviewed: boolean
  ediblePortion: number | null
  ediblePortionFdcId: number | null
  energyPublished: boolean
  nutrients: Record<string, Nutrient>
  nutrition: Nutrition
}
