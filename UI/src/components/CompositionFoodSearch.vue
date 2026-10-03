<script setup lang="ts">
import { onMounted } from 'vue'
import { catalogueApi } from '@/api/catalogue'
import type { CompositionFood } from '@/api/compositionTypes'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { compositionFoodName } from '@/domain/compositionFood'

/**
 * A search box over the open composition tables (CIQUAL 2025, BLS 4.0): type,
 * wait a beat, pick one. Each food says which table it comes from, since the
 * two measure protein and energy differently.
 *
 * `initialTerm` starts the box with a word — the name of the food already on
 * screen — and searches it, so the likely answer is one click away. Nothing is
 * picked on its own: the list is an offer.
 */
const props = defineProps<{ initialTerm?: string }>()

const emit = defineEmits<{ pick: [food: CompositionFood] }>()

const SEARCH_SIZE = 8

const { term, results, searching, failed, search, clear } = useDebouncedSearch((name) =>
  catalogueApi.composition(name, 0, SEARCH_SIZE).then((page) => page.content),
)

onMounted(() => {
  if (props.initialTerm) {
    search(props.initialTerm)
  }
})

function pick(food: CompositionFood): void {
  clear()
  emit('pick', food)
}
</script>

<template>
  <div class="search">
    <input
      class="input"
      :value="term"
      placeholder="Buscar en CIQUAL o BLS"
      aria-label="Buscar un alimento en CIQUAL o BLS"
      @input="search(($event.target as HTMLInputElement).value)"
    />
    <p v-if="searching" class="hint">Buscando…</p>
    <p v-else-if="failed" class="hint">No se pudo buscar. Comprueba la conexión con el servidor.</p>
    <ul v-if="!searching && results.length > 0" class="results">
      <li v-for="result in results" :key="result.id">
        <button class="result" type="button" :title="result.attribution" @click="pick(result)">
          <span class="name">{{ compositionFoodName(result) }}</span>
          <span class="meta">
            {{ result.sourceLabel }}
            <template v-if="result.nameEs === null"> · sin nombre en español</template>
          </span>
        </button>
      </li>
    </ul>
    <p v-else-if="!searching && !failed && term.trim() !== ''" class="hint">
      Ningún alimento de CIQUAL o BLS se llama así. Prueba con su nombre en español, en inglés o en
      francés.
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
  display: flex;
  align-items: baseline;
  gap: 8px;
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

.name {
  flex: 1 1 auto;
  min-width: 0;
}

.meta {
  flex: none;
  font-size: 10.5px;
  color: var(--ink-muted);
}

.hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}
</style>
