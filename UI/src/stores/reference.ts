import { computed, shallowRef } from 'vue'
import { referenceApi } from '@/api/reference'
import type {
  HouseholdMeasure,
  HouseholdMeasureWord,
  ReferenceProfile,
  ReferenceSource,
} from '@/api/types'

/**
 * The reference rows that change only when `reference-data/` is synced: the
 * selectable profiles, the sources and the household-measure words. Read once
 * per page load and shared by every screen that names one of them.
 */

const profiles = shallowRef<ReferenceProfile[]>([])
const sources = shallowRef<ReferenceSource[]>([])
const vocabulary = shallowRef<HouseholdMeasureWord[]>([])
let loading: Promise<void> | null = null

/** Fetches once; a failure is retried on the next call rather than cached. */
function ensure(): Promise<void> {
  if (!loading) {
    loading = Promise.all([
      referenceApi.profiles(),
      referenceApi.sources(),
      referenceApi.vocabulary(),
    ])
      .then(([profileRows, sourceRows, words]) => {
        profiles.value = profileRows
        sources.value = sourceRows
        vocabulary.value = words
      })
      .catch((cause) => {
        loading = null
        throw cause
      })
  }
  return loading
}

const selectable = computed(() => profiles.value.filter((profile) => profile.selectable))

function profile(code: string | null | undefined): ReferenceProfile | null {
  return code ? (profiles.value.find((candidate) => candidate.code === code) ?? null) : null
}

/** `AESAN 2022 · Población española adulta`, or the bare code of a profile no longer loaded. */
function profileLabel(code: string | null | undefined): string {
  if (!code) {
    return 'Sin población de referencia'
  }
  const found = profile(code)
  return found ? `${found.sourceShortName} · ${found.label}` : code
}

/** The profiles marked with the one suggested for an age — asked of the backend, which owns the rule. */
async function profilesFor(ageMonths: number | null): Promise<ReferenceProfile[]> {
  const rows = await referenceApi.profiles(ageMonths)
  return rows.filter((row) => row.selectable)
}

function source(code: string | null | undefined): ReferenceSource | null {
  return code ? (sources.value.find((candidate) => candidate.code === code) ?? null) : null
}

/**
 * The household measure a written unit names — `cdta`, `cucharadas soperas` —
 * or null. The backend's own spellings, so the two never disagree about a word.
 */
function measureOfUnit(unit: string | null | undefined): HouseholdMeasure | null {
  const key = normalise(unit)
  if (!key) {
    return null
  }
  for (const word of vocabulary.value) {
    if ([word.label, ...word.aliases].some((alias) => normalise(alias) === key)) {
      return word.code
    }
  }
  return null
}

function normalise(value: string | null | undefined): string {
  return (value ?? '')
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/\./g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

export function useReference() {
  return {
    profiles,
    sources,
    vocabulary,
    selectable,
    ensure,
    profile,
    profileLabel,
    profilesFor,
    source,
    measureOfUnit,
  }
}
