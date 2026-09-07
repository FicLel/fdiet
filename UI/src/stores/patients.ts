import { computed, ref, shallowRef } from 'vue'
import { patientsApi } from '@/api/patients'
import { dietsApi } from '@/api/diets'
import type { DietSummary, Patient } from '@/api/types'

/**
 * Who the screens are about.
 *
 * **One selection, shared by both screens.** The builder and the patient view
 * are two readings of the same week, so they must never be looking at two
 * different people: the selection lives here, at module scope, and the diet
 * stores read it rather than each holding one.
 *
 * **It is not a login.** The backend has no security layer, so switching
 * patient changes whose week is on screen and nothing else — no data is hidden
 * from anybody, and any patient's diet is reachable from any screen. That is
 * the current stage of the project, and this file is not the place that will
 * change when accounts arrive.
 *
 * Two lists, kept apart on purpose. `patients` is the caseload, and it is the
 * patient module's answer. `current` is which diet each of them is on, and it
 * is the diet module's — on the backend a diet points at its patient and never
 * the other way round, so the two are two requests and are merged here.
 */

/** Survives a reload, so the editor comes back to the person they were on. */
const SELECTED_KEY = 'fdiet.patient'

const patients = shallowRef<Patient[]>([])
const current = shallowRef<DietSummary[]>([])
const selectedId = ref<number | null>(restoreSelection())
const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const error = ref<string | null>(null)
/** Set while a patient is being added or removed, so nothing is asked twice. */
const saving = ref(false)

/**
 * A browser with no storage — a private window, or one refusing it — is not a
 * failure worth showing anybody. The selection simply starts empty.
 */
function restoreSelection(): number | null {
  try {
    const stored = window.localStorage.getItem(SELECTED_KEY)
    return stored === null ? null : Number(stored)
  } catch {
    return null
  }
}

function rememberSelection(id: number | null): void {
  try {
    if (id === null) {
      window.localStorage.removeItem(SELECTED_KEY)
    } else {
      window.localStorage.setItem(SELECTED_KEY, String(id))
    }
  } catch {
    // Nothing to do about it, and nothing worth saying: the app works either
    // way, it just opens on the first patient next time.
  }
}

/** The diet in force for a patient, or null while they have none. */
function dietOf(patientId: number): DietSummary | null {
  return current.value.find((diet) => diet.patientId === patientId) ?? null
}

const selected = computed<Patient | null>(
  () => patients.value.find((patient) => patient.id === selectedId.value) ?? null,
)

const selectedDiet = computed<DietSummary | null>(() =>
  selectedId.value === null ? null : dietOf(selectedId.value),
)

/** True once there is somebody to write a diet for. */
const any = computed(() => patients.value.length > 0)

/**
 * The caseload as the selector draws it: the name, and one line about the diet
 * they are on. A patient with none is not an error — most of them start there.
 */
const listing = computed(() =>
  patients.value.map((patient) => {
    const diet = dietOf(patient.id)
    return {
      patient,
      diet,
      meta: diet ? diet.name : 'Sin dieta asignada',
      hasDiet: diet !== null,
    }
  }),
)

/**
 * Keeps the selection on somebody who exists. A stored id from a patient since
 * deleted, or no selection at all, falls back to the first of them.
 */
function settleSelection(): void {
  const stillThere = patients.value.some((patient) => patient.id === selectedId.value)
  if (!stillThere) {
    select(patients.value[0]?.id ?? null)
  }
}

async function load(): Promise<void> {
  status.value = 'loading'
  error.value = null
  try {
    const [caseload, inForce] = await Promise.all([patientsApi.list(), dietsApi.current()])
    patients.value = caseload
    current.value = inForce
    settleSelection()
    status.value = 'ready'
  } catch (cause) {
    patients.value = []
    current.value = []
    error.value = `No se pudieron cargar los pacientes${
      cause instanceof Error ? `: ${cause.message}` : ''
    }`
    status.value = 'error'
  }
}

/** Re-reads which diet each patient is on, after a publish or a copy moved one. */
async function refreshCurrent(): Promise<void> {
  try {
    current.value = await dietsApi.current()
  } catch {
    // The board is a label beside a name. Failing to refresh it is not worth
    // an error over the week the screen is actually showing.
  }
}

function select(id: number | null): void {
  selectedId.value = id
  rememberSelection(id)
}

/**
 * Adds a patient and switches to them, which is what somebody who just typed a
 * name wants next. Returns whether it worked, so the form knows to close.
 */
async function create(name: string, notes?: string): Promise<boolean> {
  if (saving.value) {
    return false
  }
  saving.value = true
  error.value = null
  try {
    const added = await patientsApi.create({ name, notes: notes ?? null })
    patients.value = [...patients.value, added].sort((a, b) => a.name.localeCompare(b.name))
    select(added.id)
    return true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'No se pudo añadir el paciente'
    return false
  } finally {
    saving.value = false
  }
}

/**
 * Removes a patient. The backend refuses one who still has diets — their weeks
 * are the record of them — and that refusal is shown as it is written rather
 * than rephrased into something vaguer.
 */
async function remove(id: number): Promise<boolean> {
  if (saving.value) {
    return false
  }
  saving.value = true
  error.value = null
  try {
    await patientsApi.remove(id)
    patients.value = patients.value.filter((patient) => patient.id !== id)
    settleSelection()
    return true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'No se pudo quitar el paciente'
    return false
  } finally {
    saving.value = false
  }
}

function clearError(): void {
  error.value = null
}

export function usePatients() {
  return {
    // state
    patients,
    current,
    selectedId,
    status,
    error,
    saving,
    // derived
    selected,
    selectedDiet,
    listing,
    any,
    // writes
    load,
    refreshCurrent,
    select,
    create,
    remove,
    clearError,
  }
}
