import { http } from './http'
import type { DayOfWeek, DietJournal, DishScore, ExtraFood, MealType } from './types'

/** What the patient logs when they eat something the plan did not prescribe. */
export interface LogExtraFoodRequest {
  day: DayOfWeek
  name: string
  quantity: number
  unit: string
  /** One of the two, or neither — never both. */
  bedcaFoodId?: number | null
  foodItemId?: number | null
  /**
   * The household measure that weighs `1 cucharada` of a generic food. Left
   * out, the backend attaches one only when the choice is not a judgement — the
   * same rule it weighs the week by.
   */
  foodMeasureId?: number | null
}

/**
 * The patient's side of the plan.
 *
 * A score's URL is the place in the week it belongs to, because that is what a
 * score is kept against: the dish rows are replaced wholesale every time the
 * nutritionist publishes, and an id from before one addresses nothing.
 */
export const journalApi = {
  /** A whole week's scores and off-plan entries, in one request. */
  find: (dietId: number) => http.get<DietJournal>(`/journal/${dietId}`),

  /** Scores one plate, writing over any earlier opinion of the same slot. */
  score: (dietId: number, day: DayOfWeek, mealType: MealType, dishIndex: number, score: number) =>
    http.put<DishScore>(`/journal/${dietId}/scores/${day}/${mealType}/${dishIndex}`, { score }),

  /**
   * Takes a score back. There is no score of zero: having no opinion has to
   * stay out of the average rather than drag it down, so removing a rating is
   * a delete.
   */
  clearScore: (dietId: number, day: DayOfWeek, mealType: MealType, dishIndex: number) =>
    http.delete<void>(`/journal/${dietId}/scores/${day}/${mealType}/${dishIndex}`),

  logExtra: (dietId: number, entry: LogExtraFoodRequest) =>
    http.post<ExtraFood>(`/journal/${dietId}/extras`, entry),

  removeExtra: (dietId: number, extraId: number) =>
    http.delete<void>(`/journal/${dietId}/extras/${extraId}`),
}
