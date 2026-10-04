import { http, query } from './http'
import type {
  ExchangeSystem,
  FoodMeasure,
  HouseholdMeasureWord,
  MeasureCriterionRequest,
  MeasureCriterionSaved,
  MeasureUsage,
  Ration,
  ReferenceProfile,
  ReferenceSource,
} from './types'

const CRITERIA = '/reference/criteria'

/**
 * Which food a reference question is about: a CIQUAL / BLS food, the same id
 * ingredients, extras and criteria are matched by.
 */
export interface ReferenceFoodKey {
  compositionFoodId: number
}

/**
 * The reference layer: which populations a diet can be read against, the
 * rations and household measures each source publishes, and the attribution
 * every one of those figures has to be shown with.
 *
 * The published rows are loaded from `reference-data/` on the backend and are
 * read-only here. What a nutritionist writes is her own weight for a measure:
 * for one diet through `dietsApi.saveMeasure`, or for every diet, patient and
 * library recipe through the criteria below ("tu criterio"), which no re-sync
 * touches.
 */
export const referenceApi = {
  sources: () => http.get<ReferenceSource[]>('/reference/sources'),

  /** With `ageMonths`, the profile that fits that age comes back marked `suggested`. */
  profiles: (ageMonths?: number | null) =>
    http.get<ReferenceProfile[]>(
      `/reference/profiles${query({ ageMonths: ageMonths ?? undefined })}`,
    ),

  /** The rations covering one food: the profile's own first, then per-food rations of other sources. */
  rations: (food: ReferenceFoodKey, profile?: string | null) =>
    http.get<Ration[]>(`/reference/rations${query({ ...food, profile: profile ?? undefined })}`),

  /**
   * The household measures that can weigh a food, narrowed to one written unit
   * when given: that diet's own criteria (with `dietId`), then the global ones,
   * then the published rows — ranges included.
   */
  measures: (
    food: ReferenceFoodKey,
    options: { unit?: string; dietId?: number; profile?: string | null } = {},
  ) =>
    http.get<FoodMeasure[]>(
      `/reference/measures${query({
        ...food,
        unit: options.unit,
        dietId: options.dietId,
        profile: options.profile ?? undefined,
      })}`,
    ),

  vocabulary: () => http.get<HouseholdMeasureWord[]>('/reference/vocabulary'),

  exchangeSystems: (clinical = false) =>
    http.get<ExchangeSystem[]>(`/reference/exchange-systems${query({ clinical })}`),

  /** The nutritionist's global criteria, every one or one food's. Not paged. */
  criteria: (compositionFoodId?: number) =>
    http.get<FoodMeasure[]>(`${CRITERIA}${query({ compositionFoodId })}`),

  /** What a change to the criterion would reach now, in every diet. */
  criterionUsage: (id: number) => http.get<MeasureUsage>(`${CRITERIA}/${id}/usage`),

  /**
   * One per food, measure and size; a second is a 400 naming the first. Every
   * ingredient and extra it now applies to whose measure nobody picked is
   * weighed by the rule again, in every diet; the answer counts them.
   */
  createCriterion: (request: MeasureCriterionRequest) =>
    http.post<MeasureCriterionSaved>(CRITERIA, request),

  /** Live for everything it weighs; while in use only the weight and the note may change. */
  updateCriterion: (id: number, request: MeasureCriterionRequest) =>
    http.put<MeasureCriterionSaved>(`${CRITERIA}/${id}`, request),

  /** Refused (400, with the reason) while an ingredient or an extra is weighed by it. */
  deleteCriterion: (id: number) => http.delete<void>(`${CRITERIA}/${id}`),
}
