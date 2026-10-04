/**
 * Household measures and the nutritionist's own weights for them ("tu
 * criterio"), as the backend sends them. Kept apart from `types.ts` so neither
 * file outgrows the limit; re-exported there so imports stay as they were.
 */

import type { FoodState, HouseholdMeasure, PortionSize, WeightBasis } from './types'

/**
 * A household measure and what it weighs: `1 cucharada sopera · 10 ml · AESAN 2022`.
 * A range (`53–63 g`) weighs nothing and is never attached on its own.
 */
export interface FoodMeasure {
  id: number
  code: string | null
  measure: HouseholdMeasure
  measureLabel: string
  size: PortionSize | null
  count: number
  foodLabel: string | null
  compositionFoodId: number | null
  foodCategory: string | null
  keywords: string | null
  gramsMin: number | null
  gramsMax: number | null
  mlMin: number | null
  mlMax: number | null
  /** Null for a range: nothing picks a midpoint. */
  gramsPerMeasure: number | null
  state: FoodState
  weightBasis: WeightBasis
  grossGrams: number | null
  householdText: string | null
  pageRef: string | null
  note: string | null
  sourceCode: string | null
  sourceShortName: string | null
  sourceTier: number | null
  dietId: number | null
  /** The nutritionist's own criterion for this diet, not a published row. */
  dietOwn: boolean
  /** The nutritionist's own criterion for every diet ("tu criterio"), not a published row. */
  globalOwn: boolean
}

/**
 * The nutritionist's own weight for one household measure of one food: for one
 * diet (`dietsApi.saveMeasure`) or for every diet (`referenceApi.createCriterion`).
 * Exactly one of grams or ml, for one measure, edible part.
 */
export interface MeasureCriterionRequest {
  measure: HouseholdMeasure
  size?: PortionSize | null
  compositionFoodId: number
  grams?: number | null
  ml?: number | null
  note?: string | null
}

/** What a change to a criterion reaches, live: the ingredients and extras it weighs now. */
export interface MeasureUsage {
  measureId: number
  ingredients: number
  extraFoods: number
}

/**
 * What saving a criterion weighed differently (FD-054): the ingredients and
 * extras whose measure nobody picked, chosen again by the rule — usually the
 * new criterion, sometimes none. A measure a person picked is never among them.
 */
export interface MeasureReweigh {
  ingredients: number
  extraFoods: number
}

/** A global criterion as saved, and what saving it re-weighed in every diet. */
export interface MeasureCriterionSaved {
  measure: FoodMeasure
  reweighed: MeasureReweigh
}

/** A diet's own criterion as saved, and what it re-weighed in that diet. */
export interface DietMeasureSaved {
  measure: FoodMeasure
  /** `reweighed`'s total, kept from before the split. */
  attached: number
  reweighed: MeasureReweigh
}

export interface HouseholdMeasureWord {
  code: HouseholdMeasure
  label: string
  aliases: string[]
}
