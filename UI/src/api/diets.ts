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
  ingredients: RequestIngredient[]
}

export interface RequestMeal {
  type: string
  name: string
  dishes: RequestDish[]
}

/** The shape `PUT /api/diets/{id}` takes: the whole week, never a patch of it. */
export interface DietRequest {
  name: string
  startedOn: string
  days: { day: string; meals: RequestMeal[] }[]
}

export const dietsApi = {
  /** The diet in force now, ordered by day and slot. 404 when there is none. */
  active: () => http.get<Diet>('/diets/active'),

  byId: (id: number) => http.get<Diet>(`/diets/${id}`),

  /** The archived diets, without their weeks. */
  history: (page = 0, size = 20) => http.get<Page<DietSummary>>(`/diets${query({ page, size })}`),

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
