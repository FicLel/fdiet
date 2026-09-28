import { ref, shallowRef } from 'vue'
import { dietsApi } from '@/api/diets'
import type { DietRations } from '@/api/types'

/**
 * The stored week counted in rations against its reference profile.
 *
 * **What is published, not what is being typed.** The count is the backend's,
 * over the rows it holds, so a cell with unpublished changes is still counted
 * as it was stored — and the strip says so. Working it out here from the draft
 * would be a second implementation of the arithmetic, and two of them drift.
 */

const rations = shallowRef<DietRations | null>(null)
const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const error = ref<string | null>(null)
let token = 0

async function load(dietId: number | null): Promise<void> {
  const mine = ++token
  if (dietId === null) {
    rations.value = null
    status.value = 'idle'
    return
  }
  status.value = 'loading'
  error.value = null
  try {
    const answer = await dietsApi.rations(dietId)
    if (mine === token) {
      rations.value = answer
      status.value = 'ready'
    }
  } catch (cause) {
    if (mine === token) {
      rations.value = null
      error.value = `No se pudieron contar las raciones${
        cause instanceof Error ? `: ${cause.message}` : ''
      }`
      status.value = 'error'
    }
  }
}

export function useRations() {
  return { rations, status, error, load }
}
