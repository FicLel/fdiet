import { http, query } from './http'
import type { Diet, DietSummary, Dish, DishIngredient, Page } from './types'

/** What one ingredient may be corrected to; a field left out is left alone. */
export interface ResolveIngredient {
  foodItemId?: number | null
  bedcaFoodId?: number | null
  name?: string
  quantity?: number
  unit?: string
}

/** One written cell, to be read by the same parser the workbook import uses. */
export interface ParseDishRequest {
  text: string
  /** What the grid row is called; the parser names the dish after it when the cell does not. */
  slotName?: string
}

/**
 * An ingredient on the way back. Sending the ids the diet already carries keeps
 * a match the nutritionist made by hand; leaving them out lets the backend match
 * the name again, which is what an edited cell wants.
 */
export interface RequestIngredient {
  name: string
  quantity: number
  unit: string
  foodItemId?: number | null
  bedcaFoodId?: number | null
}

export interface RequestDish {
  name: string
  /**
   * The cell as written. Sent back untouched for a cell nobody edited, and left
   * out when the diet never carried one — a rebuilt sentence is not the one the
   * nutritionist typed, and storing it as if it were would make the loss
   * permanent.
   */
  rawText?: string | null
  ingredients: RequestIngredient[]
}

export interface RequestMeal {
  type: string
  name: string
  dishes: RequestDish[]
}

/**
 * The shape `PUT /api/diets/{id}` takes: the whole week, never a patch of it.
 *
 * `patientId` must be the patient the diet already belongs to. A diet does not
 * change hands through an edit — moving one would take the patient's journal
 * with it — so `copy` is how a week reaches somebody else, and it leaves the
 * original where it was.
 */
export interface DietRequest {
  patientId: number
  name: string
  startedOn: string
  days: { day: string; meals: RequestMeal[] }[]
}

/** Where a week is being copied to, and what the copy is called. */
export interface CopyDietRequest {
  /** Whose diet the copy becomes; it archives whatever they had in force. */
  patientId: number
  /** Null to keep the source's name. */
  name?: string | null
  /** Null for today. */
  startedOn?: string | null
}

export const dietsApi = {
  /**
   * One patient's diet in force, ordered by day and slot. 404 while they have
   * not been given one — which is a different thing from an error, and the
   * screens say so.
   */
  active: (patientId: number) => http.get<Diet>(`/diets/active${query({ patientId })}`),

  /**
   * Every patient's diet in force, without their weeks: who is on a diet right
   * now. One request, however many patients there are.
   */
  current: () => http.get<DietSummary[]>('/diets/current'),

  byId: (id: number) => http.get<Diet>(`/diets/${id}`),

  /** One patient's archived diets, without their weeks. */
  history: (patientId: number, page = 0, size = 20) =>
    http.get<Page<DietSummary>>(`/diets${query({ patientId, page, size })}`),

  /**
   * Writes the same week again for another patient — its days, its dishes, the
   * sentences they were typed as, and every food match already made. The source
   * is left alone, and the journal is not copied: what one patient thought of a
   * plate is their own record.
   */
  copy: (id: number, request: CopyDietRequest) =>
    http.post<Diet>(`/diets/${id}/copy`, request),

  /** The fix-up list: pass resolved=false for the ingredients still unmatched. */
  ingredients: (
    id: number,
    options: { resolved?: boolean; suggest?: boolean; page?: number; size?: number } = {},
  ) => http.get<Page<DishIngredient>>(`/diets/${id}/ingredients${query({ ...options })}`),

  resolveIngredient: (id: number, ingredientId: number, change: ResolveIngredient) =>
    http.patch<DishIngredient>(`/diets/${id}/ingredients/${ingredientId}`, change),

  /**
   * Reads a cell the nutritionist typed into a dish and its ingredients, matched
   * and priced, without storing anything. The parser lives in one place so the
   * editor and the workbook import never disagree about the same line of text.
   */
  parse: (request: ParseDishRequest) => http.post<Dish>('/diets/parse', request),

  /** Replaces a diet's whole week. What is not sent is deleted. */
  update: (id: number, week: DietRequest) => http.put<Diet>(`/diets/${id}`, week),
}
