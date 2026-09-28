import { http, query } from './http'
import type {
  ExchangeSystem,
  FoodMeasure,
  HouseholdMeasureWord,
  Ration,
  ReferenceProfile,
  ReferenceSource,
} from './types'

/**
 * The reference layer: which populations a diet can be read against, the
 * rations and household measures each source publishes, and the attribution
 * every one of those figures has to be shown with.
 *
 * Read-only from here. The rows are loaded from `reference-data/` on the
 * backend, and the one thing a nutritionist writes — a measure's weight for one
 * diet — goes through `dietsApi.saveMeasure`, because it belongs to the diet.
 */
export const referenceApi = {
  sources: () => http.get<ReferenceSource[]>('/reference/sources'),

  /** With `ageMonths`, the profile that fits that age comes back marked `suggested`. */
  profiles: (ageMonths?: number | null) =>
    http.get<ReferenceProfile[]>(
      `/reference/profiles${query({ ageMonths: ageMonths ?? undefined })}`,
    ),

  /** The rations covering one food: the profile's own first, then per-food rations of other sources. */
  rations: (bedcaFoodId: number, profile?: string | null) =>
    http.get<Ration[]>(`/reference/rations${query({ bedcaFoodId, profile: profile ?? undefined })}`),

  /**
   * The household measures that can weigh a food, narrowed to one written unit
   * when given. With `dietId`, that diet's own criteria come first.
   */
  measures: (
    bedcaFoodId: number,
    options: { unit?: string; dietId?: number; profile?: string | null } = {},
  ) =>
    http.get<FoodMeasure[]>(
      `/reference/measures${query({
        bedcaFoodId,
        unit: options.unit,
        dietId: options.dietId,
        profile: options.profile ?? undefined,
      })}`,
    ),

  vocabulary: () => http.get<HouseholdMeasureWord[]>('/reference/vocabulary'),

  exchangeSystems: (clinical = false) =>
    http.get<ExchangeSystem[]>(`/reference/exchange-systems${query({ clinical })}`),
}
