import { http, query } from './http'
import type { RequestIngredient } from './diets'
import type { Page, Recipe, RecipeUsage } from './types'

/**
 * A library recipe on the way in. Ingredients sent are kept with the matches a
 * person made; with none, the backend reads `rawText`.
 */
export interface RecipeRequest {
  name: string
  steps: string | null
  rawText: string | null
  ingredients: RequestIngredient[]
}

/**
 * The shared recipe library. A library recipe is linked, not copied: every plate
 * that serves it reads it, so `update` reaches every week that uses it at once —
 * without a publish. `usage` says which ones before anybody finds out.
 */
export const recipesApi = {
  library: (name = '', page = 0, size = 50) =>
    http.get<Page<Recipe>>(`/recipes${query({ name: name || undefined, page, size })}`),

  byId: (id: number) => http.get<Recipe>(`/recipes/${id}`),

  usage: (id: number) => http.get<RecipeUsage>(`/recipes/${id}/usage`),

  create: (request: RecipeRequest) => http.post<Recipe>('/recipes', request),

  update: (id: number, request: RecipeRequest) => http.put<Recipe>(`/recipes/${id}`, request),

  remove: (id: number) => http.delete<void>(`/recipes/${id}`),
}
