/* ---------------------------------------------------------------------------
 * The journal — the patient's side of the plan.
 *
 * The week says what to eat. These say what was thought of it and what was
 * eaten beside it, and they are the only rows a patient writes. Kept apart from
 * `types.ts` so neither file outgrows the limit; `types.ts` re-exports them.
 * ------------------------------------------------------------------------- */

import type {
  DayOfWeek,
  FoodMeasure,
  FoodState,
  MealType,
  Nutrition,
  NutritionSummary,
  PortionSize,
  UnitWording,
} from './types'
import type { CompositionSource } from './compositionTypes'
/**
 * What the patient thought of one plate, 1–5.
 *
 * **It names a slot, not a dish.** `PUT /api/diets/{id}` replaces the whole
 * week, so every dish id is new after any publish; a score keyed on one would
 * be lost each time the nutritionist edited a single cell. The place in the
 * week is what survives.
 */
export interface DishScore {
  day: DayOfWeek
  mealType: MealType
  dishIndex: number
  score: number
  scoredAt: string
}

/** Something eaten that the plan did not prescribe. */
export interface ExtraFood {
  id: number
  day: DayOfWeek
  /** What the patient wrote, kept whether or not anything matched it. */
  name: string
  quantity: number
  unit: string
  /** `unit` worded for reading; absent from an older backend. */
  unitWording?: UnitWording | null
  state: FoodState | null
  size: PortionSize | null
  /** The CIQUAL / BLS food it is matched to; at most one of this and `foodItemId`. */
  compositionFoodId: number | null
  foodItemId: number | null
  /** What the catalogue calls the food, once matched. */
  matchedName: string | null
  /** Which table a matched composition food comes from; null otherwise. */
  matchedSource: CompositionSource | null
  /** The maker, when the match came from the branded half. */
  brand: string | null
  /** The household measure that weighed `1 cucharada`, the same rule the week weighs by. */
  foodMeasureId: number | null
  measure: FoodMeasure | null
  /** Scaled to the quantity logged; null when unmatched or unweighable. */
  nutrition: Nutrition | null
  loggedAt: string
}

export interface DayExtras {
  day: DayOfWeek
  extras: ExtraFood[]
  nutrition: NutritionSummary
}

/**
 * A whole week's journal in one answer.
 *
 * `averageScore` is null while nothing has been scored — nobody rated the week
 * badly, they have not rated it — and travels with `scored`, the number of
 * plates it was worked out over.
 */
export interface DietJournal {
  dietId: number
  scores: DishScore[]
  days: DayExtras[]
  averageScore: number | null
  scored: number
}

/**
 * How much of the patient's record hangs off one diet: what deleting the diet
 * takes with it. Counted by the backend, never by the screen.
 */
export interface JournalCounts {
  dietId: number
  /** Plates the patient has scored. */
  scored: number
  /** Off-plan entries logged against the diet. */
  extras: number
}
