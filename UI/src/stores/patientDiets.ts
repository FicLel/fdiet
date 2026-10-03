import { ref, shallowRef } from 'vue'
import { dietsApi } from '@/api/diets'
import { journalApi } from '@/api/journal'
import type { DietSummary, JournalCounts } from '@/api/types'
import { usePatients } from '@/stores/patients'
import { useDietDraft } from '@/stores/dietDraft'
import { usePatientWeek } from '@/stores/patientWeek'

/**
 * One patient's diets — the one in force and the archived ones — and deleting
 * any of them.
 *
 * The diet in force comes from the patients store's board (`current`), the
 * archived ones from `dietsApi.history`: the backend answers the two questions
 * separately and they are put side by side here, in force first.
 *
 * **A delete is asked twice.** The first press reads what the diet carries —
 * scored plates and extras, counted by the backend — and the confirm states it;
 * only the second press deletes. Deleting the diet in force leaves the patient
 * with none, and the screens already showing it are sent back to that state.
 */

/** Archived diets read per request; "Ver más" reads the next page. */
const HISTORY_PAGE_SIZE = 20

type ListStatus = 'idle' | 'loading' | 'ready' | 'error'

/** A delete waiting for its second press, with what it would take along. */
export interface PendingDelete {
  diet: DietSummary
  counts: JournalCounts
}

const patientId = ref<number | null>(null)
const active = shallowRef<DietSummary | null>(null)
const archived = shallowRef<DietSummary[]>([])
const status = ref<ListStatus>('idle')
const error = ref<string | null>(null)
/** The next history page to read, or null once the last one is in. */
const nextPage = ref<number | null>(null)
/** The diet whose counts are being read, so its button can say so. */
const asking = ref<number | null>(null)
const pending = shallowRef<PendingDelete | null>(null)
const deleting = ref(false)

function failure(prefix: string, cause: unknown): string {
  return `${prefix}${cause instanceof Error ? `: ${cause.message}` : ''}`
}

function activeOf(id: number): DietSummary | null {
  return usePatients().current.value.find((diet) => diet.patientId === id) ?? null
}

/**
 * Reads one page of the history into the list. An answer that arrives after the
 * list moved on — another patient opened, or the same page already read by a
 * second press — is dropped rather than written over the list on screen.
 */
async function readHistory(id: number, page: number): Promise<void> {
  const answer = await dietsApi.history(id, page, HISTORY_PAGE_SIZE)
  if (patientId.value !== id || (page > 0 && nextPage.value !== page)) {
    return
  }
  archived.value = page === 0 ? answer.content : [...archived.value, ...answer.content]
  nextPage.value = answer.last ? null : page + 1
}

/** Reads a patient's diets afresh, the board of diets in force included. */
async function load(id: number): Promise<void> {
  patientId.value = id
  active.value = null
  archived.value = []
  pending.value = null
  error.value = null
  status.value = 'loading'
  try {
    await Promise.all([usePatients().refreshCurrent(), readHistory(id, 0)])
    // A quick switch to another patient makes this answer stale.
    if (patientId.value !== id) {
      return
    }
    active.value = activeOf(id)
    status.value = 'ready'
  } catch (cause) {
    if (patientId.value === id) {
      error.value = failure('No se pudieron cargar las dietas', cause)
      status.value = 'error'
    }
  }
}

async function loadMore(): Promise<void> {
  const id = patientId.value
  const page = nextPage.value
  if (id === null || page === null) {
    return
  }
  error.value = null
  try {
    await readHistory(id, page)
  } catch (cause) {
    error.value = failure('No se pudieron cargar más dietas', cause)
  }
}

/** First press: reads what goes with the diet, for the confirm to say. */
async function ask(diet: DietSummary): Promise<void> {
  if (asking.value !== null || deleting.value) {
    return
  }
  asking.value = diet.id
  error.value = null
  pending.value = null
  try {
    pending.value = { diet, counts: await journalApi.counts(diet.id) }
  } catch (cause) {
    error.value = failure('No se pudo comprobar qué se eliminaría', cause)
  } finally {
    asking.value = null
  }
}

function cancel(): void {
  pending.value = null
}

/**
 * Sends the screens that were showing a deleted diet back to whatever the
 * patient has now — for the diet in force, nothing, which each screen already
 * shows as "no diet yet". An unpublished edit of a deleted week has nowhere to go.
 */
function forget(dietId: number): void {
  const draft = useDietDraft()
  if (draft.diet.value?.id === dietId) {
    void draft.load()
  }
  const week = usePatientWeek()
  if (week.diet.value?.id === dietId) {
    void week.load()
  }
}

/** Second press: deletes the diet the confirm named. Answers whether it worked. */
async function confirm(): Promise<boolean> {
  const target = pending.value
  if (target === null || deleting.value) {
    return false
  }
  deleting.value = true
  error.value = null
  try {
    await dietsApi.remove(target.diet.id)
    pending.value = null
    if (active.value?.id === target.diet.id) {
      active.value = null
    }
    archived.value = archived.value.filter((diet) => diet.id !== target.diet.id)
    await usePatients().refreshCurrent()
    forget(target.diet.id)
    // With pages still unread, the server's pages have each shifted up by one
    // row: reading the next one as numbered would skip a diet. Start again from
    // the first page instead. The delete itself already worked, so a failure
    // here is reported as a failed read, not as a failed delete.
    const id = patientId.value
    if (nextPage.value !== null && id !== null) {
      readHistory(id, 0).catch((cause) => {
        error.value = failure('No se pudieron cargar las dietas', cause)
      })
    }
    return true
  } catch (cause) {
    error.value = failure('No se pudo eliminar la dieta', cause)
    return false
  } finally {
    deleting.value = false
  }
}

export function usePatientDiets() {
  return {
    // state
    patientId,
    active,
    archived,
    status,
    error,
    nextPage,
    asking,
    pending,
    deleting,
    // writes
    load,
    loadMore,
    ask,
    cancel,
    confirm,
  }
}
