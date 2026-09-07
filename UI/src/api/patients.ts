import { http } from './http'
import type { Patient } from './types'

/** A patient as they are written down. The name is required and must be free. */
export interface PatientRequest {
  name: string
  notes?: string | null
}

/**
 * The people diets are written for.
 *
 * A caseload is a handful of rows, so the listing is not paged. What each of
 * them is eating is a different question, answered in one request by
 * `dietsApi.current()` — a patient row carries no diet, because on the backend
 * a diet points at its patient and never the other way round.
 */
export const patientsApi = {
  list: () => http.get<Patient[]>('/patients'),

  byId: (id: number) => http.get<Patient>(`/patients/${id}`),

  create: (patient: PatientRequest) => http.post<Patient>('/patients', patient),

  update: (id: number, patient: PatientRequest) =>
    http.put<Patient>(`/patients/${id}`, patient),

  /**
   * Removes a patient who has no diets. One who still has diets comes back as a
   * 400: their weeks are the record of them and are not deleted as a side
   * effect of dropping the name.
   */
  remove: (id: number) => http.delete<void>(`/patients/${id}`),
}
