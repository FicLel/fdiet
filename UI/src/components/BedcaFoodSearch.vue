<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef } from 'vue'
import { catalogueApi } from '@/api/catalogue'
import type { BedcaFood } from '@/api/types'

/**
 * A search box over the composition database: type, wait a beat, pick one of
 * the few best names. Only the last search typed is ever shown.
 *
 * The backend matches the term word by word (accents, case and plurals
 * ignored) and answers best first, so `pan de molde` finds
 * `Pan blanco, de molde, tostado`. The order is shown as it comes: no ranking
 * happens here.
 */

const emit = defineEmits<{ pick: [food: BedcaFood] }>()

const SEARCH_SIZE = 8
const DEBOUNCE_MS = 300

const term = ref('')
const results = shallowRef<BedcaFood[]>([])
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
      const page = await catalogueApi.bedca(query, 0, SEARCH_SIZE)
      if (mine === token) {
        results.value = page.content
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
  }, DEBOUNCE_MS)
}

function pick(food: BedcaFood): void {
  clearTimeout(timer)
  token++
  term.value = ''
  results.value = []
  searching.value = false
  emit('pick', food)
}

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="search">
    <input
      class="input"
      :value="term"
      placeholder="Buscar un alimento genérico"
      aria-label="Buscar un alimento genérico"
      @input="search(($event.target as HTMLInputElement).value)"
    />
    <p v-if="searching" class="hint">Buscando…</p>
    <p v-else-if="failed" class="hint">No se pudo buscar. Comprueba la conexión con el servidor.</p>
    <ul v-if="!searching && results.length > 0" class="results">
      <li v-for="result in results" :key="result.id">
        <button class="result" type="button" @click="pick(result)">{{ result.name }}</button>
      </li>
    </ul>
    <p v-else-if="!searching && !failed && term.trim() !== ''" class="hint">
      Ningún alimento genérico comparte una palabra con esa búsqueda. Los nombres siguen BEDCA, de lo general a
      lo particular: «huevo», «pan blanco», «jamón serrano».
    </p>
  </div>
</template>

<style scoped>
.search {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input {
  width: 100%;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--line-input);
  border-radius: var(--radius);
  background: var(--surface);
  font-size: 12px;
  color: var(--ink-strong);
}

.results {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.result {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  background: var(--surface);
  text-align: left;
  font-size: 11.5px;
  color: var(--ink-strong);
}

.result:hover {
  border-color: var(--sage-200);
  background: var(--sage-50);
}

.hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}
</style>
