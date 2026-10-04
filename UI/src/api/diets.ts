import { http, query } from './http'
import type {
  ComposedFragment,
  Diet,
  DietImportSummary,
  DietMeasureSaved,
  DietRations,
  DietSummary,
  DishIngredient,
  FoodMeasure,
  FoodState,
  MeasureCriterionRequest,
  Page,
  PortionSize,
  Recipe,
} from './types'

/** What one ingredient may be corrected to; a field left out is left alone. */
export interface ResolveIngredient {
  foodItemId?: number | null
  compositionFoodId?: number | null
  name?: string
  quantity?: number
  unit?: string
  /** A household measure to weigh the ingredient through; it must be able to weigh its unit. */
  foodMeasureId?: number | null
}

/** Recipe text, to be read by the same parser the workbook import uses. */
export interface ParseDishRequest {
  text: string
  /** What the plate is called; the parser names the recipe after it when the text does not. */
  slotName?: string
  /** The diet the recipe is written in, so its own measure criteria are applied. */
  dietId?: number
  /**
   * The matches already held for this text. An ingredient read under one of these
   * names keeps that food (and measure, while it still weighs the unit) instead of
   * what its name would match; an edited name matches none, and parse decides.
   */
  keep?: KeptMatch[]
}

/**
 * A match carried through a re-read of the text: the ingredient's name as the
 * backend read it, and exactly one of the two food ids.
 */
export interface KeptMatch {
  name: string
  compositionFoodId?: number
  foodItemId?: number
  /** The household measure it is weighed through now; re-validated by the backend. */
  foodMeasureId?: number
}

/**
 * An ingredient on the way back. Sending the ids the diet already carries keeps
 * a match the nutritionist made by hand; leaving them out lets the backend match
 * the name again, which is what an edited cell wants.
 */
export interface RequestIngredient {
  name: string
  quantity: number
  /** The upper end of a range nobody has settled; sent back so a publish keeps it a range. */
  quantityMax?: number | null
  unit: string
  foodItemId?: number | null
  compositionFoodId?: number | null
  state?: FoodState | null
  size?: PortionSize | null
  foodMeasureId?: number | null
}

/** A plate's own recipe on the way back: the text as written, the steps, and what was read. */
export interface RequestRecipe {
  name?: string | null
  rawText: string | null
  steps: string | null
  ingredients: RequestIngredient[]
}

/**
 * A plate on the way back. `name` is the description the patient reads. It names
 * its recipe by `recipeId` — a library recipe, or the private one it already has,
 * kept as stored — or writes its own in `recipe`; never both, and neither for a
 * plate that is a description only.
 */
export interface RequestDish {
  name: string
  servings: number
  recipeId?: number | null
  recipe?: RequestRecipe | null
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
  /**
   * Null keeps the profile the diet has — on create, it suggests one from the
   * patient's age. A blank string is "none".
   */
  referenceProfileCode?: string | null
  clinical?: boolean | null
}

/** What a diet is read against. Fields left out are left alone; a blank profile takes it off. */
export interface DietSettings {
  name?: string
  referenceProfileCode?: string | null
  clinical?: boolean
}

export interface ImportDietRequest {
  file: File
  patientId: number
  sheet?: string
  name?: string
  startedOn?: string
  referenceProfile?: string | null
  clinical?: boolean
}

/**
 * One food to be written into a cell: a weight, or a count of a household
 * measure. The backend answers with the text to append, so the editor still has
 * no parser of its own.
 */
export interface ComposeRequest {
  compositionFoodId: number
  grams?: number
  foodMeasureId?: number
  count?: number
  state?: FoodState | null
  dietId?: number
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
   * Reads recipe text the nutritionist typed into its ingredients, matched and
   * priced, without storing anything. The parser lives in one place so the
   * editor and the workbook import never disagree about the same line of text.
   */
  parse: (request: ParseDishRequest) => http.post<Recipe>('/diets/parse', request),

  /**
   * Deletes a diet, in force or archived, with its week, its journal, its own
   * measure criteria and its private recipes. Library recipes stay. Deleting the
   * one in force leaves the patient with none: nothing archived comes back.
   */
  remove: (id: number) => http.delete<void>(`/diets/${id}`),

  /** Replaces a diet's whole week. What is not sent is deleted. */
  update: (id: number, week: DietRequest) => http.put<Diet>(`/diets/${id}`, week),

  /** Stores a week — possibly an empty one — as the patient's diet in force, archiving theirs. */
  create: (week: DietRequest) => http.post<Diet>('/diets', week),

  /** Reads one sheet of a workbook into the patient's diet in force, archiving theirs. */
  importWorkbook: (request: ImportDietRequest) => {
    const form = new FormData()
    form.set('file', request.file)
    form.set('patientId', String(request.patientId))
    for (const key of ['sheet', 'name', 'startedOn'] as const) {
      const value = request[key]
      if (value) {
        form.set(key, value)
      }
    }
    // Unlike the others a blank is meaningful here: no profile at all.
    if (request.referenceProfile !== undefined && request.referenceProfile !== null) {
      form.set('referenceProfile', request.referenceProfile)
    }
    if (request.clinical !== undefined) {
      form.set('clinical', String(request.clinical))
    }
    return http.postForm<DietImportSummary>('/diets/import', form)
  },

  /** Changes the name, profile or clinical flag without touching the week. */
  updateSettings: (id: number, settings: DietSettings) =>
    http.patch<Diet>(`/diets/${id}`, settings),

  /**
   * The week in rations against its profile (or another one, to compare), with
   * the counts of what could not be counted and why. Orientative, derived on read.
   */
  rations: (id: number, profile?: string | null) =>
    http.get<DietRations>(`/diets/${id}/rations${query({ profile: profile ?? undefined })}`),

  compose: (request: ComposeRequest) => http.post<ComposedFragment>('/diets/compose', request),

  /** The diet's own measure criteria. */
  measures: (id: number) => http.get<FoodMeasure[]>(`/diets/${id}/measures`),

  /** Writes (or rewrites) a criterion and attaches it wherever it is now the one chosen. */
  saveMeasure: (id: number, request: MeasureCriterionRequest) =>
    http.put<DietMeasureSaved>(`/diets/${id}/measures`, request),

  deleteMeasure: (id: number, measureId: number) =>
    http.delete<void>(`/diets/${id}/measures/${measureId}`),
}
