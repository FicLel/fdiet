import { onBeforeUnmount, ref, shallowRef } from 'vue'

/** How long typing has to pause before a search is sent. */
export const SEARCH_DEBOUNCE_MS = 300

/**
 * A search box's state: type, wait a beat, show what came back. Only the last
 * search typed is ever shown; an answer to an earlier term that lands late is
 * dropped. The order the backend answers in is kept as it comes.
 */
export function useDebouncedSearch<T>(fetch: (term: string) => Promise<T[]>) {
  const term = ref('')
  const results = shallowRef<T[]>([])
  const searching = ref(false)
  const failed = ref(false)

  let timer: ReturnType<typeof setTimeout> | undefined
  let token = 0

  function search(value: string): void {
    term.value = value
    clearTimeout(timer)
    // Claimed now, not when the timer fires: a request already in flight for an
    // earlier term must not land while this one is still waiting its beat.
    const mine = ++token
    const query = value.trim()
    failed.value = false
    if (query === '') {
      results.value = []
      searching.value = false
      return
    }
    searching.value = true
    timer = setTimeout(async () => {
      try {
        const found = await fetch(query)
        if (mine === token) {
          results.value = found
        }
      } catch {
        if (mine === token) {
          results.value = []
          failed.value = true
        }
      } finally {
        if (mine === token) {
          searching.value = false
        }
      }
    }, SEARCH_DEBOUNCE_MS)
  }

  /** Empties the box and forgets any search still on its way. */
  function clear(): void {
    clearTimeout(timer)
    token++
    term.value = ''
    results.value = []
    searching.value = false
  }

  onBeforeUnmount(() => clearTimeout(timer))

  return { term, results, searching, failed, search, clear }
}
