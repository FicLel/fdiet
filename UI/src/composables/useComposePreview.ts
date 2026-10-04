import { onBeforeUnmount, shallowRef, watch } from 'vue'
import { dietsApi, type ComposeRequest } from '@/api/diets'
import type { DishIngredient } from '@/api/types'

/** How long the composer's inputs have to settle before the reading is asked for. */
export const PREVIEW_DEBOUNCE_MS = 300

/**
 * What the backend reads back from the fragment the composer is about to write,
 * asked before the nutritionist presses "Añadir": `compose` stores nothing, so
 * asking early costs one request and tells her — a state mismatch, say — while
 * she can still change her mind. The editor still reads no text itself.
 *
 * Only the latest request's answer is kept; one that fails just leaves no
 * reading (the add itself reports errors).
 */
export function useComposePreview(request: () => ComposeRequest | null) {
  const reading = shallowRef<DishIngredient | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined
  let token = 0

  watch(
    () => JSON.stringify(request()),
    () => {
      clearTimeout(timer)
      const mine = ++token
      reading.value = null
      const asked = request()
      if (!asked) {
        return
      }
      timer = setTimeout(async () => {
        try {
          const composed = await dietsApi.compose(asked)
          if (mine === token) {
            reading.value = composed.ingredient
          }
        } catch {
          // No reading; pressing "Añadir" says what went wrong.
        }
      }, PREVIEW_DEBOUNCE_MS)
    },
    { immediate: true },
  )

  onBeforeUnmount(() => clearTimeout(timer))

  return { reading }
}
